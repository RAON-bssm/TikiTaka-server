package com.raon.tikitaka.application.review;

import com.raon.tikitaka.application.review.in.ReviewUseCase;
import com.raon.tikitaka.application.review.out.AiReviewPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReviewService implements ReviewUseCase {

    private final AiReviewPort aiReviewPort;

    @Override
    public AiReviewResult evaluate(String mission, String content, byte[] image, String contentType) {
        return aiReviewPort.review(mission, content, image, contentType);
    }
}
