package com.raon.tikitaka.application.review.in;

import com.raon.tikitaka.application.review.AiReviewResult;

public interface ReviewUseCase {

    AiReviewResult evaluate(String mission, String content, byte[] image, String contentType);
}
