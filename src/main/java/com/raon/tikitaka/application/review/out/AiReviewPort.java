package com.raon.tikitaka.application.review.out;

import com.raon.tikitaka.application.review.AiReviewResult;

public interface AiReviewPort {

    /**
     * 실패는 성격에 따라 두 예외로 나눠 던진다.
     * - {@link com.raon.tikitaka.application.review.AiReviewRetryableException}: 429, 5xx, 타임아웃, 네트워크 오류,
     *   응답 형식 오류. 다시 시도하면 성공할 수 있다
     * - {@link com.raon.tikitaka.application.review.AiReviewRejectedException}: 429를 뺀 4xx. 다시 보내도 같다
     * 응답은 받았지만 점수가 비어 있으면 score를 null로 돌려주고, 범위 검증은 ReviewService가 한다.
     */
    AiReviewResult review(String mission, String content, byte[] image, String contentType);
}
