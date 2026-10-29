package com.raon.tikitaka.adapter.post.out;

import com.raon.tikitaka.application.post.out.PostReviewQueuePort;
import com.raon.tikitaka.domain.post.Post;
import com.raon.tikitaka.domain.post.PostStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PostReviewQueuePersistenceAdapter implements PostReviewQueuePort {

    /** advisory lock 키 앞부분. 다른 용도의 advisory lock과 키가 겹치지 않게 구분한다 */
    private static final int SEASON_LOCK_NAMESPACE = 0x5EA50;

    private final PostJpaRepository postJpaRepository;

    @Override
    public long countClaimable(LocalDateTime now, LocalDateTime staleBefore) {
        return postJpaRepository.countClaimable(now, staleBefore);
    }

    /**
     * 잠그기(SKIP LOCKED)와 선점 시각 기록이 같은 트랜잭션이어야 다른 틱이 같은 글을 가져가지 않는다.
     * 호출하는 서비스가 트랜잭션을 연다.
     */
    @Override
    public List<Post> claim(int limit, LocalDateTime now, LocalDateTime staleBefore) {
        List<UUID> ids = postJpaRepository.lockClaimableIds(now, staleBefore, limit);
        if (ids.isEmpty()) {
            return List.of();
        }
        postJpaRepository.markClaimed(ids, now);
        return postJpaRepository.findAllWithStageByIdIn(ids);
    }

    @Override
    public void complete(UUID postId, int score, String aiReview) {
        postJpaRepository.completeReview(postId, score, aiReview);
    }

    @Override
    public void scheduleRetry(UUID postId, int attempts, LocalDateTime nextAt) {
        postJpaRepository.scheduleReviewRetry(postId, attempts, nextAt);
    }

    @Override
    public void finish(UUID postId, PostStatus status, int attempts) {
        postJpaRepository.finishReview(postId, status, attempts);
    }

    @Override
    public void release(UUID postId) {
        postJpaRepository.releaseReviewClaim(postId);
    }

    @Override
    public void reset(UUID postId) {
        postJpaRepository.resetReview(postId);
    }

    @Override
    public int expirePendingBySeason(int season) {
        return postJpaRepository.expirePendingBySeason(season);
    }

    @Override
    public void lockSeasonShared(int season) {
        postJpaRepository.lockSeasonShared(SEASON_LOCK_NAMESPACE, season);
    }

    @Override
    public void lockSeasonExclusive(int season) {
        postJpaRepository.lockSeasonExclusive(SEASON_LOCK_NAMESPACE, season);
    }
}
