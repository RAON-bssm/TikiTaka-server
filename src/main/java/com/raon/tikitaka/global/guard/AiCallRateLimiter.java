package com.raon.tikitaka.global.guard;

import com.raon.tikitaka.global.config.PostReviewProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 게시물 심사 워커의 Gemini 호출 속도 제한기. 최근 60초 안에 내준 허가 수로 세는 슬라이딩 윈도우다.
 * 첫 시도와 재시도 모두 이 제한기를 거친다. 허가를 받지 못한 글은 선점하지 않고 그대로 두므로
 * 기다리는 동안 시도 횟수(review_attempts)가 늘지 않는다.
 *
 * 미션 생성(GeminiMissionAdapter)은 같은 키를 쓰지만 이 제한기를 거치지 않는다. 그래서 한도를
 * Gemini 한도보다 1회 적게 잡아 그 몫을 남겨둔다(post-review.max-calls-per-minute).
 * 허가 시각은 허가를 받은 때이고 실제 호출은 S3 이미지를 읽은 뒤라 조금 늦다. 그 차이만큼 윈도우 경계에서
 * Gemini가 보는 호출이 한도를 넘을 수 있지만, 남겨둔 1회 안에서 흡수된다.
 * RequestGuard처럼 서버 단일 인스턴스 전제의 인메모리 구현이다.
 */
@Component
public class AiCallRateLimiter {

    static final Duration WINDOW = Duration.ofSeconds(60);

    private final int maxCalls;
    private final Clock clock;
    private final Deque<Instant> grantedAt = new ArrayDeque<>();

    @Autowired
    public AiCallRateLimiter(PostReviewProperties properties) {
        this(properties.maxCallsPerMinute(), Clock.systemUTC());
    }

    AiCallRateLimiter(int maxCalls, Clock clock) {
        this.maxCalls = maxCalls;
        this.clock = clock;
    }

    /**
     * 허가 묶음. grantedAt은 돌려줄 때 어느 허가인지 가리키는 값이다.
     */
    public record Permits(int count, Instant grantedAt) {
    }

    /**
     * 최대 requested개의 허가를 바로 내준다. 기다리지 않고, 남은 몫이 없으면 count가 0이다.
     */
    public synchronized Permits acquire(int requested) {
        Instant now = clock.instant();
        evictExpired(now);
        int granted = Math.max(0, Math.min(requested, maxCalls - grantedAt.size()));
        for (int i = 0; i < granted; i++) {
            grantedAt.addLast(now);
        }
        return new Permits(granted, now);
    }

    public int tryAcquire(int requested) {
        return acquire(requested).count();
    }

    /**
     * 받았지만 쓰지 않은 허가를 돌려준다. 선점할 글이 모자랐거나 AI를 부르지 않고 끝난 경우다.
     * 받을 때 찍힌 시각의 허가를 지운다. 가장 최근 허가를 지우면 다른 허가가 먼저 윈도우를 벗어나
     * 60초 안의 실제 호출이 한도를 넘을 수 있다. 이미 윈도우를 벗어난 허가는 지울 것이 없다.
     */
    public synchronized void release(Instant grantedAt, int count) {
        for (int i = 0; i < count; i++) {
            if (!this.grantedAt.removeFirstOccurrence(grantedAt)) {
                return;
            }
        }
    }

    private void evictExpired(Instant now) {
        Instant windowStart = now.minus(WINDOW);
        while (!grantedAt.isEmpty() && !grantedAt.peekFirst().isAfter(windowStart)) {
            grantedAt.removeFirst();
        }
    }

    /** 테스트에서 윈도우 안의 허가 수를 확인하는 용도 */
    synchronized int grantedInWindow() {
        evictExpired(clock.instant());
        return grantedAt.size();
    }
}
