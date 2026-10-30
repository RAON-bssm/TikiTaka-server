package com.raon.tikitaka.application.review;

import com.raon.tikitaka.application.review.out.AiReviewPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReviewServiceTest {

    private final AiReviewPort aiReviewPort = mock(AiReviewPort.class);
    private final ReviewService reviewService = new ReviewService(aiReviewPort);

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {-1, 101})
    void 점수가_없거나_범위_밖이면_재시도_대상_형식_오류(Integer score) {
        when(aiReviewPort.review(any(), any(), any(), any())).thenReturn(new AiReviewResult(score, "리뷰"));

        assertThatThrownBy(() -> reviewService.evaluate("미션", "내용", new byte[0], "image/jpeg"))
                .isInstanceOf(AiReviewInvalidResponseException.class)
                .isInstanceOf(AiReviewRetryableException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 100})
    void 범위_안의_점수는_그대로_돌려준다(int score) {
        when(aiReviewPort.review(any(), any(), any(), any())).thenReturn(new AiReviewResult(score, "리뷰"));

        assertThat(reviewService.evaluate("미션", "내용", new byte[0], "image/jpeg").score()).isEqualTo(score);
    }

    @Test
    void 어댑터의_실패_예외는_그대로_올라간다() {
        AiReviewRejectedException rejected = new AiReviewRejectedException("거절", 400, null);
        when(aiReviewPort.review(any(), any(), any(), any())).thenThrow(rejected);

        assertThatThrownBy(() -> reviewService.evaluate("미션", "내용", new byte[0], "image/jpeg"))
                .isSameAs(rejected);
    }
}
