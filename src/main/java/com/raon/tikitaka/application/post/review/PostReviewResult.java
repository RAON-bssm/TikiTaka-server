package com.raon.tikitaka.application.post.review;

/**
 * @param aiCalled 이번 처리에서 Gemini를 실제로 불렀는지. 부르지 않았으면 워커가 호출 허가를 돌려준다
 */
public record PostReviewResult(ReviewOutcome outcome, boolean aiCalled) {
}
