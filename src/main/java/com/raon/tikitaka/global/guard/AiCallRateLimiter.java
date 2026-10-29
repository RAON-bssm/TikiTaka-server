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
     * 최대 requested개의 허가를 바로 내준다. 기다리지 않고, 남은 몫이 없으면 0이다.
     */
    public synchronized int tryAcquire(int requested) {
        Instant now = clock.instant();
        evictExpired(now);
        int granted = Math.max(0, Math.min(requested, maxCalls - grantedAt.size()));
        for (int i = 0; i < granted; i++) {
            grantedAt.addLast(now);
        }
        return granted;
    }

    /**
     * 받았지만 쓰지 않은 허가를 돌려준다. 선점할 글이 모자랐거나 AI를 부르지 않고 끝난 경우다.
     * 가장 최근에 내준 허가부터 지운다.
     */
    public synchronized void release(int count) {
        for (int i = 0; i < count && !grantedAt.isEmpty(); i++) {
            grantedAt.removeLast();
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
