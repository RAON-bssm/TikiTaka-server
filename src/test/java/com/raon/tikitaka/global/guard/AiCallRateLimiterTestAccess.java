package com.raon.tikitaka.global.guard;

import java.time.Duration;

/**
 * 다른 패키지 테스트(워커 테스트)에서 시계를 움직일 수 있는 제한기를 만드는 용도.
 */
public final class AiCallRateLimiterTestAccess {

    private AiCallRateLimiterTestAccess() {
    }

    public static Fixture fixture(int maxCalls) {
        AiCallRateLimiterTest.MutableClock clock = new AiCallRateLimiterTest.MutableClock();
        return new Fixture(new AiCallRateLimiter(maxCalls, clock), clock);
    }

    public record Fixture(AiCallRateLimiter limiter, AiCallRateLimiterTest.MutableClock clock) {

        public void advance(Duration duration) {
            clock.advance(duration);
        }

        public int grantedInWindow() {
            return limiter.grantedInWindow();
        }
    }
}
