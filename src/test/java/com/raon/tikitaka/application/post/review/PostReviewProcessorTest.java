package com.raon.tikitaka.application.post.review;

import com.raon.tikitaka.adapter.review.out.GeminiAdapterTestAccess;
import com.raon.tikitaka.application.post.in.CompletePostReviewUseCase;
import com.raon.tikitaka.application.ranking.out.SeasonResultRepositoryPort;
import com.raon.tikitaka.application.review.AiReviewInvalidResponseException;
import com.raon.tikitaka.application.review.AiReviewRejectedException;
import com.raon.tikitaka.application.review.AiReviewResult;
import com.raon.tikitaka.application.review.AiReviewRetryableException;
import com.raon.tikitaka.application.review.ReviewService;
import com.raon.tikitaka.application.review.in.ReviewUseCase;
import com.raon.tikitaka.application.review.out.AiReviewPort;
import com.raon.tikitaka.application.storage.StoredImage;
import com.raon.tikitaka.application.storage.out.StoragePort;
import com.raon.tikitaka.domain.post.PostStatus;
import com.raon.tikitaka.global.guard.RequestGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    private final LocalDateTime claimedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    private final ClaimedReview claimed = new ClaimedReview(postId, authorId, 3L, "미션", 1, "내용", "photo.jpg", 0,
            claimedAt.minusSeconds(10), claimedAt);

    private PostReviewProcessor processorWith(RequestGuard guard) {
        return new PostReviewProcessor(queueService, completeUseCase, reviewUseCase, storagePort, seasonResultPort,
                guard);
    }

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
        verify(queueService, never()).recordFailure(any(), any(), any());
    }

    @Test
    void 응답_429면_Gemini가_준_대기_시간으로_재시도를_예약한다() {
        when(reviewUseCase.evaluate(any(), any(), any(), any()))
                .thenThrow(new AiReviewRetryableException("429", Duration.ofSeconds(17), null));
        when(queueService.recordFailure(postId, claimedAt, Duration.ofSeconds(17))).thenReturn(PostStatus.PENDING);

        PostReviewResult result = processor.review(claimed);

        assertThat(result).isEqualTo(new PostReviewResult(ReviewOutcome.RETRY, true));
        verify(completeUseCase, never()).completeReview(any(), anyInt(), any());
    }

    @Test
    void 시도_한도에_닿으면_FAILED() {
        when(reviewUseCase.evaluate(any(), any(), any(), any()))
                .thenThrow(new AiReviewRetryableException("503", null, null));
        when(queueService.recordFailure(postId, claimedAt, null)).thenReturn(PostStatus.FAILED);

        assertThat(processor.review(claimed).outcome()).isEqualTo(ReviewOutcome.FAILED);
    }

    @Test
    void 응답_401이_시도_한도까지_반복되면_거절이_아니라_FAILED() {
        // 키 만료처럼 계속 401이 와도 REJECTED로 저장하지 않는다. 마지막 시도의 실패 기록이 FAILED로 바꾼다
        when(reviewUseCase.evaluate(any(), any(), any(), any())).thenThrow(GeminiAdapterTestAccess.classify(401));
        when(queueService.recordFailure(postId, claimedAt, null)).thenReturn(PostStatus.PENDING, PostStatus.PENDING,
                PostStatus.PENDING, PostStatus.PENDING, PostStatus.FAILED);

        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThat(processor.review(claimed).outcome()).isEqualTo(ReviewOutcome.RETRY);
        }
        assertThat(processor.review(claimed).outcome()).isEqualTo(ReviewOutcome.FAILED);
        verify(queueService, never()).finish(any(), any(), any());
    }

    @Test
    void 선점이_만료돼_다른_작업이_가져간_글은_실패를_기록하지_못하고_SKIPPED() {
        when(reviewUseCase.evaluate(any(), any(), any(), any()))
                .thenThrow(new AiReviewRetryableException("503", null, null));
        // 큐 서비스는 선점 주인이 아니면 아무것도 바꾸지 않고 null을 돌려준다
        when(queueService.recordFailure(postId, claimedAt, null)).thenReturn(null);

        assertThat(processor.review(claimed).outcome()).isEqualTo(ReviewOutcome.SKIPPED);
    }

    @Test
    void 선점이_만료된_뒤_포인트_키_409를_만나면_선점을_풀지_못하고_SKIPPED() {
        when(reviewUseCase.evaluate(any(), any(), any(), any())).thenReturn(new AiReviewResult(77, "좋아요"));
        when(completeUseCase.completeReview(postId, 77, "좋아요"))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "busy"));
        when(queueService.release(postId, claimedAt)).thenReturn(false);

        assertThat(processor.review(claimed).outcome()).isEqualTo(ReviewOutcome.SKIPPED);
    }

    @Test
    void 응답_형식_오류도_재시도_대상() {
        when(reviewUseCase.evaluate(any(), any(), any(), any()))
                .thenThrow(new AiReviewInvalidResponseException("score=null"));
        when(queueService.recordFailure(postId, claimedAt, null)).thenReturn(PostStatus.PENDING);

        assertThat(processor.review(claimed).outcome()).isEqualTo(ReviewOutcome.RETRY);
    }

    @Test
    void 응답_400이면_재시도하지_않고_REJECTED() {
        when(reviewUseCase.evaluate(any(), any(), any(), any()))
                .thenThrow(new AiReviewRejectedException("400", 400, null));
        when(queueService.finish(postId, claimedAt, PostStatus.REJECTED)).thenReturn(true);

        PostReviewResult result = processor.review(claimed);

        assertThat(result).isEqualTo(new PostReviewResult(ReviewOutcome.REJECTED, true));
        verify(queueService, never()).recordFailure(any(), any(), any());
    }

    @Test
    void 시즌이_이미_확정됐으면_AI를_부르지_않고_EXPIRED() {
        when(seasonResultPort.existsBySeason(1)).thenReturn(true);
        when(queueService.finish(postId, claimedAt, PostStatus.EXPIRED)).thenReturn(true);

        PostReviewResult result = processor.review(claimed);

        assertThat(result).isEqualTo(new PostReviewResult(ReviewOutcome.EXPIRED, false));
        verify(reviewUseCase, never()).evaluate(any(), any(), any(), any());
        verify(completeUseCase, never()).completeReview(any(), anyInt(), any());
    }

    @Test
    void 이미지를_읽지_못하면_AI를_부르지_않고_실패로_센다() {
        when(storagePort.download("photo.jpg")).thenThrow(new IllegalStateException("S3 오류"));
        when(queueService.recordFailure(postId, claimedAt, null)).thenReturn(PostStatus.PENDING);

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
        verify(queueService, never()).recordFailure(any(), any(), any());
    }

    @Test
    void 포인트_키_409가_한_번_나도_같은_스레드에서_다시_반영해_AI를_다시_부르지_않는다() {
        RequestGuard guard = mock(RequestGuard.class);
        PostReviewProcessor processor = processorWith(guard);
        when(reviewUseCase.evaluate(any(), any(), any(), any())).thenReturn(new AiReviewResult(77, "좋아요"));
        when(guard.executeWaiting(eq(RequestGuard.pointsKey(authorId)), anyString(), ArgumentMatchers.<Supplier<Boolean>>any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "busy"))
                .thenReturn(true);

        PostReviewResult result = processor.review(claimed);

        assertThat(result).isEqualTo(new PostReviewResult(ReviewOutcome.DONE, true));
        verify(reviewUseCase, times(1)).evaluate(any(), any(), any(), any());
        verify(guard, times(2)).executeWaiting(anyString(), anyString(), ArgumentMatchers.<Supplier<Boolean>>any());
        verify(queueService, never()).release(any(), any());
        verify(queueService, never()).recordFailure(any(), any(), any());
    }

    @Test
    void 포인트_키_409가_반영_시도_한도까지_이어지면_실패로_세지_않고_선점만_푼다() {
        RequestGuard guard = mock(RequestGuard.class);
        PostReviewProcessor processor = processorWith(guard);
        when(reviewUseCase.evaluate(any(), any(), any(), any())).thenReturn(new AiReviewResult(77, "좋아요"));
        when(guard.executeWaiting(anyString(), anyString(), ArgumentMatchers.<Supplier<Boolean>>any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "busy"));
        when(queueService.release(postId, claimedAt)).thenReturn(true);

        PostReviewResult result = processor.review(claimed);

        assertThat(result).isEqualTo(new PostReviewResult(ReviewOutcome.DEFERRED, true));
        verify(guard, times(3)).executeWaiting(anyString(), anyString(), ArgumentMatchers.<Supplier<Boolean>>any());
        verify(queueService).release(postId, claimedAt);
        verify(queueService, never()).recordFailure(any(), any(), any());
    }

    @Test
    void 포인트_키_409가_아닌_반영_오류는_다시_시도하지_않고_실패로_센다() {
        RequestGuard guard = mock(RequestGuard.class);
        PostReviewProcessor processor = processorWith(guard);
        when(reviewUseCase.evaluate(any(), any(), any(), any())).thenReturn(new AiReviewResult(77, "좋아요"));
        when(guard.executeWaiting(anyString(), anyString(), ArgumentMatchers.<Supplier<Boolean>>any()))
                .thenThrow(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "boom"));
        when(queueService.recordFailure(postId, claimedAt, null)).thenReturn(PostStatus.PENDING);

        assertThat(processor.review(claimed).outcome()).isEqualTo(ReviewOutcome.RETRY);
        verify(guard, times(1)).executeWaiting(anyString(), anyString(), ArgumentMatchers.<Supplier<Boolean>>any());
    }

    @Test
    void 반영_중_예외는_실패로_세서_끝없이_돌지_않게_한다() {
        when(reviewUseCase.evaluate(any(), any(), any(), any())).thenReturn(new AiReviewResult(77, "좋아요"));
        when(completeUseCase.completeReview(postId, 77, "좋아요"))
                .thenThrow(new IllegalStateException("라운드의 평균 동네 인원(C)이 없습니다."));
        when(queueService.recordFailure(postId, claimedAt, null)).thenReturn(PostStatus.PENDING);

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

    @Test
    void 안전_필터에_막힌_응답은_Gemini를_한_번만_부르고_재시도_없이_REJECTED() {
        // 실제 Gemini 어댑터의 응답 해석을 그대로 거친다. 응답만 안전 필터 차단 모양으로 고정한다
        String blocked = "{\"promptFeedback\": {\"blockReason\": \"SAFETY\"}}";
        AtomicInteger calls = new AtomicInteger();
        AiReviewPort port = (mission, content, image, contentType) -> {
            calls.incrementAndGet();
            return GeminiAdapterTestAccess.parse(blocked);
        };
        PostReviewProcessor realReview = new PostReviewProcessor(queueService, completeUseCase,
                new ReviewService(port), storagePort, seasonResultPort, requestGuard);
        when(queueService.finish(postId, claimedAt, PostStatus.REJECTED)).thenReturn(true);

        PostReviewResult result = realReview.review(claimed);

        assertThat(result).isEqualTo(new PostReviewResult(ReviewOutcome.REJECTED, true));
        assertThat(calls).hasValue(1);
        // 재시도 예약(review_next_at)은 recordFailure로만 생긴다
        verify(queueService, never()).recordFailure(any(), any(), any());
        verify(completeUseCase, never()).completeReview(any(), anyInt(), any());
    }
}
