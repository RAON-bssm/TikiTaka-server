package com.raon.tikitaka.application.post.review;

import com.raon.tikitaka.application.post.in.CompletePostReviewUseCase;
import com.raon.tikitaka.application.ranking.out.SeasonResultRepositoryPort;
import com.raon.tikitaka.application.review.AiReviewInvalidResponseException;
import com.raon.tikitaka.application.review.AiReviewRejectedException;
import com.raon.tikitaka.application.review.AiReviewResult;
import com.raon.tikitaka.application.review.AiReviewRetryableException;
import com.raon.tikitaka.application.review.in.ReviewUseCase;
import com.raon.tikitaka.application.storage.StoredImage;
import com.raon.tikitaka.application.storage.out.StoragePort;
import com.raon.tikitaka.domain.post.PostStatus;
import com.raon.tikitaka.global.guard.RequestGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 심사 한 건의 결과 분류. 대기열 상태 변경과 심사 완료 반영은 mock이고, 포인트 키 가드는 실제 객체다.
 */
class PostReviewProcessorTest {

    private final PostReviewQueueService queueService = mock(PostReviewQueueService.class);
    private final CompletePostReviewUseCase completeUseCase = mock(CompletePostReviewUseCase.class);
    private final ReviewUseCase reviewUseCase = mock(ReviewUseCase.class);
    private final StoragePort storagePort = mock(StoragePort.class);
    private final SeasonResultRepositoryPort seasonResultPort = mock(SeasonResultRepositoryPort.class);
    private final RequestGuard requestGuard = new RequestGuard();
    private final PostReviewProcessor processor = new PostReviewProcessor(queueService, completeUseCase, reviewUseCase,
            storagePort, seasonResultPort, requestGuard);

    private final UUID postId = UUID.randomUUID();
    private final UUID authorId = UUID.randomUUID();
    private final ClaimedReview claimed = new ClaimedReview(postId, authorId, 3L, "미션", 1, "내용", "photo.jpg", 0,
            LocalDateTime.now().minusSeconds(10), LocalDateTime.now());

    @BeforeEach
    void setUp() {
        when(storagePort.download("photo.jpg")).thenReturn(new StoredImage(new byte[]{1, 2, 3}, "image/png"));
    }

    @Test
    void 심사에_성공하면_점수를_반영하고_DONE() {
        when(reviewUseCase.evaluate(any(), any(), any(), any())).thenReturn(new AiReviewResult(77, "좋아요"));
        when(completeUseCase.completeReview(postId, 77, "좋아요")).thenReturn(true);

        PostReviewResult result = processor.review(claimed);

        assertThat(result).isEqualTo(new PostReviewResult(ReviewOutcome.DONE, true));
        verify(completeUseCase).completeReview(postId, 77, "좋아요");
        verify(queueService, never()).recordFailure(any(), any());
    }

    @Test
    void 응답_429면_Gemini가_준_대기_시간으로_재시도를_예약한다() {
        when(reviewUseCase.evaluate(any(), any(), any(), any()))
                .thenThrow(new AiReviewRetryableException("429", Duration.ofSeconds(17), null));
        when(queueService.recordFailure(postId, Duration.ofSeconds(17))).thenReturn(PostStatus.PENDING);

        PostReviewResult result = processor.review(claimed);

        assertThat(result).isEqualTo(new PostReviewResult(ReviewOutcome.RETRY, true));
        verify(completeUseCase, never()).completeReview(any(), anyInt(), any());
    }

    @Test
    void 시도_한도에_닿으면_FAILED() {
        when(reviewUseCase.evaluate(any(), any(), any(), any()))
                .thenThrow(new AiReviewRetryableException("503", null, null));
        when(queueService.recordFailure(postId, null)).thenReturn(PostStatus.FAILED);

        assertThat(processor.review(claimed).outcome()).isEqualTo(ReviewOutcome.FAILED);
    }

    @Test
    void 응답_형식_오류도_재시도_대상() {
        when(reviewUseCase.evaluate(any(), any(), any(), any()))
                .thenThrow(new AiReviewInvalidResponseException("score=null"));
        when(queueService.recordFailure(postId, null)).thenReturn(PostStatus.PENDING);

        assertThat(processor.review(claimed).outcome()).isEqualTo(ReviewOutcome.RETRY);
    }

    @Test
    void 응답_400이면_재시도하지_않고_REJECTED() {
        when(reviewUseCase.evaluate(any(), any(), any(), any()))
                .thenThrow(new AiReviewRejectedException("400", 400, null));
        when(queueService.finish(postId, PostStatus.REJECTED)).thenReturn(true);

        PostReviewResult result = processor.review(claimed);

        assertThat(result).isEqualTo(new PostReviewResult(ReviewOutcome.REJECTED, true));
        verify(queueService, never()).recordFailure(any(), any());
    }

    @Test
    void 시즌이_이미_확정됐으면_AI를_부르지_않고_EXPIRED() {
        when(seasonResultPort.existsBySeason(1)).thenReturn(true);
        when(queueService.finish(postId, PostStatus.EXPIRED)).thenReturn(true);

        PostReviewResult result = processor.review(claimed);

        assertThat(result).isEqualTo(new PostReviewResult(ReviewOutcome.EXPIRED, false));
        verify(reviewUseCase, never()).evaluate(any(), any(), any(), any());
        verify(completeUseCase, never()).completeReview(any(), anyInt(), any());
    }

    @Test
    void 이미지를_읽지_못하면_AI를_부르지_않고_실패로_센다() {
        when(storagePort.download("photo.jpg")).thenThrow(new IllegalStateException("S3 오류"));
        when(queueService.recordFailure(postId, null)).thenReturn(PostStatus.PENDING);

        PostReviewResult result = processor.review(claimed);

        assertThat(result).isEqualTo(new PostReviewResult(ReviewOutcome.RETRY, false));
        verify(reviewUseCase, never()).evaluate(any(), any(), any(), any());
    }

    @Test
    void 심사_중에_삭제된_글은_반영되지_않는다() {
        when(reviewUseCase.evaluate(any(), any(), any(), any())).thenReturn(new AiReviewResult(77, "좋아요"));
        // completeReview가 잠근 행을 다시 읽어 is_active=false면 아무것도 하지 않는다
        when(completeUseCase.completeReview(postId, 77, "좋아요")).thenReturn(false);

        assertThat(processor.review(claimed).outcome()).isEqualTo(ReviewOutcome.SKIPPED);
        verify(queueService, never()).recordFailure(any(), any());
    }

    @Test
    void 포인트_키를_기다리다_409면_실패로_세지_않고_선점만_푼다() throws Exception {
        when(reviewUseCase.evaluate(any(), any(), any(), any())).thenReturn(new AiReviewResult(77, "좋아요"));
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        // 상점 구매가 포인트 키를 5초 넘게 잡고 있는 상황
        CompletableFuture<Void> purchase = CompletableFuture.runAsync(() ->
                requestGuard.executeWaiting(RequestGuard.pointsKey(authorId), "busy", () -> {
                    holding.countDown();
                    try {
                        release.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }));
        assertThat(holding.await(5, TimeUnit.SECONDS)).isTrue();

        PostReviewResult result = processor.review(claimed);
        release.countDown();
        purchase.get(5, TimeUnit.SECONDS);

        assertThat(result).isEqualTo(new PostReviewResult(ReviewOutcome.DEFERRED, true));
        verify(queueService).release(postId);
        verify(queueService, never()).recordFailure(any(), any());
        verify(completeUseCase, never()).completeReview(any(), anyInt(), any());
    }

    @Test
    void 반영_중_예외는_실패로_세서_끝없이_돌지_않게_한다() {
        when(reviewUseCase.evaluate(any(), any(), any(), any())).thenReturn(new AiReviewResult(77, "좋아요"));
        when(completeUseCase.completeReview(postId, 77, "좋아요"))
                .thenThrow(new IllegalStateException("라운드의 평균 동네 인원(C)이 없습니다."));
        when(queueService.recordFailure(postId, null)).thenReturn(PostStatus.PENDING);

        assertThat(processor.review(claimed).outcome()).isEqualTo(ReviewOutcome.RETRY);
    }

    @Test
    void 저장소가_content_type을_주지_않으면_파일_이름으로_추측한다() {
        when(storagePort.download("photo.jpg")).thenReturn(new StoredImage(new byte[]{1}, null));
        when(reviewUseCase.evaluate(any(), any(), any(), any())).thenReturn(new AiReviewResult(50, "보통"));
        when(completeUseCase.completeReview(postId, 50, "보통")).thenReturn(true);

        processor.review(claimed);

        verify(reviewUseCase).evaluate("미션", "내용", new byte[]{1}, "image/jpeg");
    }
}
