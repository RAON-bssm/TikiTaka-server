package com.raon.tikitaka.global.guard;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class AiCallRateLimiterTest {

    private final MutableClock clock = new MutableClock();
    private final AiCallRateLimiter limiter = new AiCallRateLimiter(4, clock);

    @Test
    void 윈도우_안에서는_최대_호출_수까지만_허가한다() {
        assertThat(limiter.tryAcquire(10)).isEqualTo(4);
        assertThat(limiter.tryAcquire(1)).isZero();

        clock.advance(Duration.ofSeconds(59));
        assertThat(limiter.tryAcquire(1)).isZero();
    }

    @Test
    void 시간이_지나면_오래된_허가부터_빠지고_다시_허가한다() {
        assertThat(limiter.tryAcquire(2)).isEqualTo(2);
        clock.advance(Duration.ofSeconds(30));
        assertThat(limiter.tryAcquire(2)).isEqualTo(2);

        // 처음 두 건만 60초가 지났다. 30초에 받은 두 건은 아직 윈도우 안이다
        clock.advance(Duration.ofSeconds(30));
        assertThat(limiter.tryAcquire(4)).isEqualTo(2);
        assertThat(limiter.grantedInWindow()).isEqualTo(4);
    }

    @Test
    void 쓰지_않은_허가를_돌려주면_다시_쓸_수_있다() {
        assertThat(limiter.tryAcquire(4)).isEqualTo(4);

        limiter.release(3);

        assertThat(limiter.grantedInWindow()).isEqualTo(1);
        assertThat(limiter.tryAcquire(4)).isEqualTo(3);
    }

    @Test
    void 남은_허가보다_많이_돌려줘도_음수가_되지_않는다() {
        limiter.tryAcquire(1);

        limiter.release(5);

        assertThat(limiter.grantedInWindow()).isZero();
        assertThat(limiter.tryAcquire(10)).isEqualTo(4);
    }

    public static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-29T00:00:00Z");

        public void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
