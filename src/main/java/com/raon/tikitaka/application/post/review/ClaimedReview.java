package com.raon.tikitaka.application.post.review;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 워커가 선점한 심사 대상 한 건. 선점 트랜잭션 안에서 필요한 값을 미리 꺼내 둔다.
 * 이후 처리는 트랜잭션 밖(AI 호출 동안 DB 커넥션을 잡지 않기 위해)에서 한다.
 *
 * @param attempts  선점 시점까지 실패한 횟수
 * @param claimedAt 선점한 시각(마이크로초 단위). createdAt과의 차이가 대기열 대기 시간이고, 결과를 기록할 때
 *                  이 글을 아직 이 작업이 잡고 있는지 확인하는 값이기도 하다(review_started_at과 같은 값)
 */
public record ClaimedReview(
        UUID postId,
        UUID authorId,
        Long boardId,
        String mission,
        int season,
        String content,
        String postImage,
        int attempts,
        LocalDateTime createdAt,
        LocalDateTime claimedAt
) {
}
