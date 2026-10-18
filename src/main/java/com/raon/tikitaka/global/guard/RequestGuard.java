package com.raon.tikitaka.global.guard;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 같은 키의 요청이 이미 처리 중이면 두 번째 요청을 실행하지 않고 바로 거절하는 인메모리 가드.
 * 더블탭이나 재시도로 "확인하고 → 저장하는" 작업이 동시에 두 번 도는 것을 막는다.
 * 내용이 같은 요청을 막는 게 아니라, 처리 중인 요청과 겹친 요청만 막는다.
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

    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    public static String pointsKey(UUID userId) {
        return "points:" + userId;
    }

    /**
     * key가 처리 중이면 409(ResponseStatusException)를 던지고, 아니면 action을 실행한 뒤 키를 해제한다.
     * action이 예외를 던져도 키는 반드시 해제된다.
     */
    public <T> T execute(String key, String conflictMessage, Supplier<T> action) {
        if (!inFlight.add(key)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, conflictMessage);
        }
        try {
            return action.get();
        } finally {
            inFlight.remove(key);
        }
    }

    public void execute(String key, String conflictMessage, Runnable action) {
        execute(key, conflictMessage, () -> {
            action.run();
            return null;
        });
    }

    /**
     * 409 대신 조용히 건너뛰는 변형. idempotent한 API(좋아요)에서 처리 중인 중복을 성공으로 응답할 때 쓴다.
     *
     * @return action을 실행했으면 true, 같은 키가 처리 중이라 건너뛰었으면 false
     */
    public boolean runIfIdle(String key, Runnable action) {
        if (!inFlight.add(key)) {
            return false;
        }
        try {
            action.run();
            return true;
        } finally {
            inFlight.remove(key);
        }
    }
}
