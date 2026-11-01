package com.raon.tikitaka.application.post.in;

import com.raon.tikitaka.application.post.review.ClaimedReview;
import com.raon.tikitaka.application.post.review.PostReviewResult;

import java.util.List;

/**
 * 게시물 AI 심사 워커가 쓰는 유스케이스. 선점(claim)과 한 건 처리(review)를 나눠
 * 워커가 호출 허가를 받은 만큼만 선점하고, 선점한 글을 각자 스레드에서 처리하게 한다.
 */
public interface ProcessPostReviewUseCase {

    /** 지금 선점할 수 있는 활성 PENDING 글 수. 백오프 중이거나 다른 틱이 심사 중인 글은 세지 않는다 */
    long countClaimable();

    /** 심사할 글을 최대 limit개 선점한다. 짧은 트랜잭션 하나로 끝나고 커밋 후 돌려준다 */
    List<ClaimedReview> claim(int limit);

    /** 선점한 글 한 건을 심사한다. 트랜잭션 밖에서 AI를 부르고 결과에 따라 상태를 바꾼다 */
    PostReviewResult review(ClaimedReview claimed);
}
