package com.raon.tikitaka.application.review;

import com.raon.tikitaka.application.review.in.ReviewUseCase;
import com.raon.tikitaka.application.review.out.AiReviewPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReviewService implements ReviewUseCase {

    private final AiReviewPort aiReviewPort;

    /**
     * 점수가 없거나 0~100 밖이면 응답 형식 오류로 보고 재시도 대상 예외를 던진다.
     * 누락 시 asInt()가 0을 돌려주는 함정 때문에 GeminiAdapter가 null을 반환하게 했고 여기서 거른다.
     * 동기 업로드와 심사 워커가 같은 검증을 거치도록 컨트롤러에서 이리로 옮겼다.
     */
    @Override
    public AiReviewResult evaluate(String mission, String content, byte[] image, String contentType) {
        AiReviewResult result = aiReviewPort.review(mission, content, image, contentType);
        Integer score = result.score();
        if (score == null || score < 0 || score > 100) {
            throw new AiReviewInvalidResponseException("AI 심사 점수가 올바르지 않습니다. score=" + score);
        }
        return result;
    }
}
