package com.raon.tikitaka.application.review.in;

import com.raon.tikitaka.application.review.AiReviewResult;

public interface ReviewUseCase {

    /**
     * 돌려주는 결과의 score는 항상 0~100이다. 실패는 AiReviewPort와 같은 두 예외로 나뉘고,
     * 점수 누락이나 범위 밖은 AiReviewInvalidResponseException(재시도 대상)이다.
     */
    AiReviewResult evaluate(String mission, String content, byte[] image, String contentType);
}
