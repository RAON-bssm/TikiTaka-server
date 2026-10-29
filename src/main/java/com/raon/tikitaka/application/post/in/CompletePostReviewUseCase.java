package com.raon.tikitaka.application.post.in;

import java.util.UUID;

public interface CompletePostReviewUseCase {

    /**
     * 심사를 통과한 글에 점수를 기록하고 DONE으로 바꾼 뒤 포인트와 랭킹 점수를 반영한다.
     * 글이 삭제됐거나 이미 PENDING이 아니면(이미 반영됨, 시즌 확정으로 EXPIRED 등) 아무것도 하지 않고 false다.
     * 같은 글에 두 번 불려도 한 번만 반영된다.
     * 포인트를 바꾸므로 호출하는 쪽이 RequestGuard.pointsKey(작성자)로 감싸야 한다.
     */
    boolean completeReview(UUID postId, int score, String aiReview);
}
