package com.raon.tikitaka.application.post.in;

import java.util.UUID;

public interface RetryPostReviewUseCase {

    /**
     * FAILED 글을 다시 심사 대기열에 넣는다. 시도 횟수와 백오프를 지우고 PENDING으로 되돌린다.
     * 글이 없거나 삭제됐으면 404, FAILED가 아니면 409다.
     */
    void retryReview(UUID postId);
}
