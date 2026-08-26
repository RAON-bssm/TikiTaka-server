package com.raon.tikitaka.application.review.out;

import com.raon.tikitaka.application.review.AiReviewResult;

public interface AiReviewPort {

    AiReviewResult review(String mission, String content, byte[] image, String contentType);
}
