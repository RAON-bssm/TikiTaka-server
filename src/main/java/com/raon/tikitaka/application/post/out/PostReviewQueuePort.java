package com.raon.tikitaka.application.post.out;

import com.raon.tikitaka.domain.post.Post;
import com.raon.tikitaka.domain.post.PostStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 게시물 AI 심사 대기열. post 테이블의 PENDING 행을 그대로 대기열로 쓴다.
 * 상태를 바꾸는 메서드는 updated_at(게시물 수정 일시)을 건드리지 않는다.
 * 상태를 바꾸기 전에 PostRepositoryPort.findByIdForUpdate로 같은 트랜잭션에서 행을 잠그고 상태를 확인해야 한다.
 */
public interface PostReviewQueuePort {

    long countPending();

    /**
     * 심사할 글을 오래된 순으로 limit개 선점한다. 선점 시각만 기록하고 시도 횟수는 올리지 않는다.
     * 다른 트랜잭션이 잠근 행은 건너뛰고, staleBefore보다 먼저 선점된 채 끝나지 않은 글은 다시 가져간다.
     * 돌려주는 글에는 board, match, stage가 로딩돼 있다.
     */
    List<Post> claim(int limit, LocalDateTime now, LocalDateTime staleBefore);

    void complete(UUID postId, int score, String aiReview);

    void scheduleRetry(UUID postId, int attempts, LocalDateTime nextAt);

    /** FAILED, REJECTED, EXPIRED처럼 심사를 끝내는 상태로 바꾼다 */
    void finish(UUID postId, PostStatus status, int attempts);

    void release(UUID postId);

    void reset(UUID postId);
}
