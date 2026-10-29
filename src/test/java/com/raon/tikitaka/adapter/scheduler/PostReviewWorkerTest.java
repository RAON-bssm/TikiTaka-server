package com.raon.tikitaka.adapter.scheduler;

import com.raon.tikitaka.application.post.in.ProcessPostReviewUseCase;
import com.raon.tikitaka.application.post.review.ClaimedReview;
import com.raon.tikitaka.application.post.review.PostReviewResult;
import com.raon.tikitaka.application.post.review.ReviewOutcome;
import com.raon.tikitaka.global.guard.AiCallRateLimiter;
import com.raon.tikitaka.global.guard.AiCallRateLimiterTestAccess;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 워커 틱의 허가-선점-처리 순서. 실행기는 호출 스레드에서 바로 돌려 틱 하나의 결과를 바로 확인한다.
 */
class PostReviewWorkerTest {

    private final ProcessPostReviewUseCase useCase = mock(ProcessPostReviewUseCase.class);
    private final AiCallRateLimiterTestAccess.Fixture limiter = AiCallRateLimiterTestAccess.fixture(4);

    @Test
    void 대기_글이_없으면_허가도_선점도_하지_않는다() {
        when(useCase.countClaimable()).thenReturn(0L);

        worker(1).tick();

        verify(useCase, never()).claim(anyInt());
        assertThat(limiter.grantedInWindow()).isZero();
    }

    @Test
    void 빈_자리와_남은_허가_중_작은_수만큼만_선점한다() {
        when(useCase.countClaimable()).thenReturn(10L);
        when(useCase.claim(anyInt())).thenReturn(List.of());

        worker(8).tick();

        // 동시 심사 수는 8이지만 분당 한도가 4라 4건만 선점을 시도한다
        verify(useCase).claim(4);
    }

    @Test
    void 허가가_없으면_글을_선점하지_않는다() {
        when(useCase.countClaimable()).thenReturn(10L);
        limiter.limiter().tryAcquire(4);

        worker(1).tick();

        verify(useCase, never()).claim(anyInt());
    }

    @Test
    void 덜_선점했으면_남은_허가를_돌려준다() {
        when(useCase.countClaimable()).thenReturn(3L);
        when(useCase.claim(3)).thenReturn(List.of(claimed()));
        when(useCase.review(any())).thenReturn(new PostReviewResult(ReviewOutcome.DONE, true));

        worker(3).tick();

        assertThat(limiter.grantedInWindow()).isEqualTo(1);
    }

    @Test
    void AI를_부르지_않고_끝난_글은_허가를_돌려준다() {
        when(useCase.countClaimable()).thenReturn(1L);
        when(useCase.claim(1)).thenReturn(List.of(claimed()));
        when(useCase.review(any())).thenReturn(new PostReviewResult(ReviewOutcome.EXPIRED, false));

        worker(1).tick();

        assertThat(limiter.grantedInWindow()).isZero();
    }

    @Test
    void 재시도도_같은_제한기를_거쳐_분당_한도를_넘지_않는다() {
        // 같은 글이 계속 일시 오류로 재시도되는 상황. 틱마다 다시 선점되지만 허가는 분당 4번뿐이다
        when(useCase.countClaimable()).thenReturn(1L);
        when(useCase.claim(1)).thenReturn(List.of(claimed()));
        when(useCase.review(any())).thenReturn(new PostReviewResult(ReviewOutcome.RETRY, true));
        PostReviewWorker worker = worker(1);

        for (int i = 0; i < 10; i++) {
            worker.tick();
        }

        verify(useCase, times(4)).claim(1);
        verify(useCase, times(4)).review(any());
    }

    @Test
    void 제한기에_막힌_동안에는_선점하지_않아_시도_횟수가_늘지_않는다() {
        when(useCase.countClaimable()).thenReturn(1L);
        when(useCase.claim(1)).thenReturn(List.of(claimed()));
        when(useCase.review(any())).thenReturn(new PostReviewResult(ReviewOutcome.RETRY, true));
        PostReviewWorker worker = worker(1);
        for (int i = 0; i < 4; i++) {
            worker.tick();
        }

        // 한도를 다 쓴 뒤의 틱. 선점도 심사도 일어나지 않으므로 실패 기록(시도 횟수 증가)이 생길 수 없다
        worker.tick();
        worker.tick();
        verify(useCase, times(4)).claim(1);

        // 60초가 지나면 다시 허가가 나온다
        limiter.advance(Duration.ofSeconds(61));
        worker.tick();
        verify(useCase, times(5)).claim(1);
    }

    @Test
    void 한_건의_예외가_다른_글_처리를_막지_않는다() {
        ClaimedReview first = claimed();
        ClaimedReview second = claimed();
        when(useCase.countClaimable()).thenReturn(2L);
        when(useCase.claim(2)).thenReturn(List.of(first, second));
        when(useCase.review(first)).thenThrow(new IllegalStateException("DB 오류"));
        when(useCase.review(second)).thenReturn(new PostReviewResult(ReviewOutcome.DONE, true));

        worker(2).tick();

        verify(useCase).review(second);
    }

    @Test
    void 선점이_실패하면_허가를_모두_돌려준다() {
        when(useCase.countClaimable()).thenReturn(2L);
        when(useCase.claim(2)).thenThrow(new IllegalStateException("DB 오류"));

        worker(2).tick();

        assertThat(limiter.grantedInWindow()).isZero();
    }

    private PostReviewWorker worker(int concurrency) {
        return new PostReviewWorker(useCase, limiter.limiter(), concurrency, new DirectExecutorService());
    }

    private static ClaimedReview claimed() {
        return new ClaimedReview(UUID.randomUUID(), UUID.randomUUID(), 3L, "미션", 1, "내용", "photo.jpg", 0,
                LocalDateTime.now(), LocalDateTime.now());
    }

    /** 제출한 작업을 호출 스레드에서 바로 실행한다 */
    private static final class DirectExecutorService extends AbstractExecutorService {
        private boolean shutdown;

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return new ArrayList<>();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }
}
