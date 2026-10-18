package com.raon.tikitaka.global.guard;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 같은 키의 요청이 동시에 겹치는 것을 막는 인메모리 가드. 키마다 락 하나를 쓴다.
 * 내용이 같은 요청을 막는 게 아니라, 처리 중인 요청과 겹친 요청만 다룬다.
 *
 * 겹친 요청을 다루는 방식이 두 가지다.
 * - {@link #execute}: 기다리지 않고 바로 409로 거절한다. 더블탭처럼 두 번째 요청이 불필요한 경우(구매, 뽑기, 삭제 등)
 * - {@link #executeWaiting}: 앞선 요청이 끝날 때까지 기다린 뒤 순서대로 실행한다.
 *   두 번째 요청도 반드시 처리돼야 하는 경우(좋아요 후 곧바로 취소, 게시물 작성의 포인트 적립 단계)
 * 두 방식은 같은 키에서 서로를 배제한다.
 *
 * 반드시 @Transactional 서비스 호출을 바깥에서 감싸야 한다(컨트롤러에서 유스케이스 호출 전체를 감싼다).
 * 키는 action이 끝난 뒤, 즉 트랜잭션 커밋 이후에 풀려야 다음 요청이 커밋된 상태를 읽는다.
 *
 * 서버가 단일 인스턴스(EC2 컨테이너 1개)라는 전제다. 인스턴스를 여러 대로 늘리면
 * 서로의 처리 중 상태를 모르므로 Redis나 DB 락으로 바꿔야 한다.
 */
@Component
public class RequestGuard {

    /**
     * 포인트를 바꾸는 모든 경로(상점 구매, 가샤폰, 게시물 작성 적립)가 같은 키를 써야
     * Users.point의 읽고-계산하고-저장 사이 갱신 유실이 막힌다.
     */
    public static final String POINTS_BUSY_MESSAGE = "이전 요청을 처리하는 중이에요.";

    /** 기다리는 방식에서 앞선 요청을 기다리는 최대 시간. 톰캣 스레드를 오래 붙잡지 않도록 짧게 둔다 */
    static final long WAIT_TIMEOUT_MILLIS = 5_000;

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    public static String pointsKey(UUID userId) {
        return "points:" + userId;
    }

    /**
     * key가 처리 중이면 기다리지 않고 409(ResponseStatusException)를 던지고, 아니면 action을 실행한 뒤 키를 해제한다.
     * action이 예외를 던져도 키는 반드시 해제된다.
     */
    public <T> T execute(String key, String conflictMessage, Supplier<T> action) {
        Entry entry = acquireEntry(key);
        if (!entry.lock.tryLock()) {
            releaseEntry(key);
            throw conflict(conflictMessage);
        }
        return runLocked(key, entry, action);
    }

    public void execute(String key, String conflictMessage, Runnable action) {
        execute(key, conflictMessage, () -> {
            action.run();
            return null;
        });
    }

    /**
     * key가 처리 중이면 끝날 때까지 기다렸다가(먼저 온 순서대로) 실행한다.
     * {@link #WAIT_TIMEOUT_MILLIS} 안에 순서가 오지 않으면 409를 던진다.
     */
    public <T> T executeWaiting(String key, String conflictMessage, Supplier<T> action) {
        Entry entry = acquireEntry(key);
        boolean locked;
        try {
            locked = entry.lock.tryLock(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            releaseEntry(key);
            throw conflict(conflictMessage);
        }
        if (!locked) {
            releaseEntry(key);
            throw conflict(conflictMessage);
        }
        return runLocked(key, entry, action);
    }

    public void executeWaiting(String key, String conflictMessage, Runnable action) {
        executeWaiting(key, conflictMessage, () -> {
            action.run();
            return null;
        });
    }

    private <T> T runLocked(String key, Entry entry, Supplier<T> action) {
        try {
            return action.get();
        } finally {
            entry.lock.unlock();
            releaseEntry(key);
        }
    }

    /** 락을 잡기 전에 참조를 먼저 올려, 기다리는 요청이 있는 동안 엔트리가 지워지지 않게 한다 */
    private Entry acquireEntry(String key) {
        return entries.compute(key, (k, existing) -> {
            Entry entry = existing != null ? existing : new Entry();
            entry.refs++;
            return entry;
        });
    }

    private void releaseEntry(String key) {
        entries.compute(key, (k, entry) -> {
            if (entry == null || --entry.refs <= 0) {
                return null;
            }
            return entry;
        });
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    /** refs는 entries.compute 안에서만 바뀌므로 별도 동기화가 필요 없다 */
    private static final class Entry {
        final ReentrantLock lock = new ReentrantLock(true);
        int refs;
    }

    /** 테스트에서 엔트리가 남지 않고 정리되는지 확인하는 용도 */
    int trackedKeyCount() {
        return entries.size();
    }

    /** 테스트에서 key의 락을 기다리는 요청 수를 확인하는 용도. sleep 없이 대기열 진입을 확인한다 */
    int queuedCount(String key) {
        Entry entry = entries.get(key);
        return entry == null ? 0 : entry.lock.getQueueLength();
    }
}
