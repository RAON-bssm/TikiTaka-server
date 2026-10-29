package com.raon.tikitaka.adapter.post.out;

import com.raon.tikitaka.domain.post.Post;
import com.raon.tikitaka.domain.post.PostStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostJpaRepository extends JpaRepository<Post, UUID> {

    @Query("""
            select p from Post p
            join fetch p.userId
            join fetch p.teamLocation
            where p.board.boardId = :boardId and p.isActive = true
              and p.userId.status <> com.raon.tikitaka.domain.enums.UserStatus.WITHDRAWN
              and p.status = com.raon.tikitaka.domain.post.PostStatus.DONE
            order by p.createdAt desc
            """)
    List<Post> findAllActiveByBoardId(@Param("boardId") Long boardId);

    /**
     * findAllActiveByBoardId에서 viewerId가 차단한 유저의 게시물을 뺀 것.
     * (:viewerId is null or ...) 한 쿼리로 합치면 PostgreSQL이 null UUID 파라미터의 타입을 추론하지 못해
     * "could not determine data type of parameter" 오류가 날 수 있어, 비로그인용과 쿼리를 나눴다.
     */
    @Query("""
            select p from Post p
            join fetch p.userId
            join fetch p.teamLocation
            where p.board.boardId = :boardId and p.isActive = true
              and p.userId.status <> com.raon.tikitaka.domain.enums.UserStatus.WITHDRAWN
              and p.status = com.raon.tikitaka.domain.post.PostStatus.DONE
              and not exists (select 1 from UserBlock b
                               where b.blocker.userId = :viewerId
                                 and b.blocked.userId = p.userId.userId)
            order by p.createdAt desc
            """)
    List<Post> findAllActiveByBoardIdExcludingBlockedBy(@Param("boardId") Long boardId,
                                                        @Param("viewerId") UUID viewerId);

    /**
     * 상세·수정·삭제·좋아요·신고가 함께 쓴다. 탈퇴한 작성자의 글은 데이터는 남기고 여기서 빈 결과가 되므로
     * 모든 경로에서 404로 끝난다.
     * 심사 상태는 여기서 거르지 않는다. 상세는 작성자 본인과 관리자에게 DONE이 아닌 글도 보여야 해서
     * 경로마다 서비스에서 상태를 확인한다.
     */
    @Query("""
            select p from Post p
            join fetch p.userId
            join fetch p.teamLocation
            where p.postId = :postId and p.isActive = true
              and p.userId.status <> com.raon.tikitaka.domain.enums.UserStatus.WITHDRAWN
            """)
    Optional<Post> findActiveById(@Param("postId") UUID postId);

    /**
     * 마이페이지 "게시물 보관함"용. 여러 라운드·게시판을 넘나드는 목록이라
     * board까지 그래프째 즉시 로딩해서 응답에 게시판·미션 정보를 같이 담는다.
     */
    @Query("""
            select p from Post p
            join fetch p.teamLocation
            join fetch p.board b
            join fetch b.match m
            join fetch m.team1
            join fetch m.team2
            join fetch m.stage
            where p.userId.userId = :userId and p.isActive = true
            order by p.createdAt desc
            """)
    List<Post> findAllActiveByUserId(@Param("userId") UUID userId);

    /**
     * id로 글 행을 잠그고 읽는다. 심사 완료·삭제·대기열 상태 변경이 같은 글에서 겹치지 않게 한다.
     * findActiveById와 달리 is_active와 작성자 탈퇴 여부를 거르지 않는다. 탈퇴한 작성자의 글도 심사는 끝내야 하고
     * (탈퇴해도 점수 유지 방침), 거르면 "없는 글"로 오인해 영원히 PENDING으로 남는다. 조건 확인은 호출하는 쪽이 한다.
     * 다른 엔티티를 fetch join하지 않는 이유는 PostgreSQL의 FOR UPDATE가 조인한 행까지 잠그기 때문이다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Post p where p.postId = :postId")
    Optional<Post> findByIdForUpdate(@Param("postId") UUID postId);

    // ===== 심사 대기열 =====

    @Query("select count(p) from Post p where p.status = com.raon.tikitaka.domain.post.PostStatus.PENDING and p.isActive = true")
    long countActivePending();

    /**
     * 심사할 PENDING 글을 오래된 순으로 limit개 잠근다. 다른 워커 트랜잭션이 잠근 행은 건너뛴다(SKIP LOCKED).
     * 백오프 중(review_next_at이 미래)이거나 최근에 선점된(review_started_at이 staleBefore 이후) 글은 제외한다.
     * 선점 후 staleBefore보다 오래 끝나지 않은 글은 서버가 처리 도중 죽은 것으로 보고 다시 가져간다.
     */
    @Query(value = """
            select p.post_id
              from post p
             where p.status = 'PENDING'
               and p.is_active = true
               and (p.review_next_at is null or p.review_next_at <= :now)
               and (p.review_started_at is null or p.review_started_at < :staleBefore)
             order by p.created_at
             limit :limit
               for update skip locked
            """, nativeQuery = true)
    List<UUID> lockClaimableIds(@Param("now") LocalDateTime now,
                                @Param("staleBefore") LocalDateTime staleBefore,
                                @Param("limit") int limit);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Post p set p.reviewStartedAt = :now where p.postId in :postIds")
    int markClaimed(@Param("postIds") Collection<UUID> postIds, @Param("now") LocalDateTime now);

    /**
     * 선점한 글을 심사에 필요한 정보(미션, 시즌)와 함께 읽는다. 작성자 상태는 보지 않는다.
     */
    @Query("""
            select p from Post p
            join fetch p.board b
            join fetch b.match m
            join fetch m.stage
            where p.postId in :postIds
            order by p.createdAt
            """)
    List<Post> findAllWithStageByIdIn(@Param("postIds") Collection<UUID> postIds);

    /**
     * 심사 통과. 상태 컬럼은 벌크 update로만 바꿔 updated_at(게시물 수정 일시)이 바뀌지 않게 한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Post p
               set p.score = :score, p.aiReview = :aiReview,
                   p.status = com.raon.tikitaka.domain.post.PostStatus.DONE,
                   p.reviewNextAt = null, p.reviewStartedAt = null
             where p.postId = :postId
            """)
    int completeReview(@Param("postId") UUID postId, @Param("score") int score, @Param("aiReview") String aiReview);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Post p
               set p.reviewAttempts = :attempts, p.reviewNextAt = :nextAt, p.reviewStartedAt = null
             where p.postId = :postId
            """)
    int scheduleReviewRetry(@Param("postId") UUID postId, @Param("attempts") int attempts,
                            @Param("nextAt") LocalDateTime nextAt);

    /**
     * 심사를 끝내는 상태(FAILED, REJECTED, EXPIRED)로 바꾼다. 실패 횟수도 같이 기록한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Post p
               set p.status = :status, p.reviewAttempts = :attempts,
                   p.reviewNextAt = null, p.reviewStartedAt = null
             where p.postId = :postId
            """)
    int finishReview(@Param("postId") UUID postId, @Param("status") PostStatus status,
                     @Param("attempts") int attempts);

    /**
     * 시즌 확정 직전에 그 시즌 라운드에 속한 PENDING 글을 한 번에 EXPIRED로 바꾼다.
     * 심사 완료 처리 중인 글은 행 잠금이 풀릴 때까지 기다렸다가 다시 판단하므로(READ COMMITTED),
     * 그 사이 DONE이 된 글은 건너뛰고 점수는 확정 계산에 들어간다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update post p
               set status = 'EXPIRED', review_next_at = null, review_started_at = null
              from board b
              join match m on m.match_id = b.match_id
              join stage s on s.stage_id = m.stage_id
             where p.board_id = b.board_id
               and s.season = :season
               and p.status = 'PENDING'
            """, nativeQuery = true)
    int expirePendingBySeason(@Param("season") int season);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Post p set p.reviewStartedAt = null where p.postId = :postId")
    int releaseReviewClaim(@Param("postId") UUID postId);

    /** 관리자 재심사. 시도 횟수와 백오프를 지우고 처음 업로드한 상태로 되돌린다 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Post p
               set p.status = com.raon.tikitaka.domain.post.PostStatus.PENDING, p.reviewAttempts = 0,
                   p.reviewNextAt = null, p.reviewStartedAt = null
             where p.postId = :postId
            """)
    int resetReview(@Param("postId") UUID postId);
}
