package com.raon.tikitaka.adapter.ranking.out;

import com.raon.tikitaka.application.ranking.UserRankRow;
import com.raon.tikitaka.domain.ranking.UserRanking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRankingJpaRepository extends JpaRepository<UserRanking, Long> {

    /**
     * 개인 점수 누적 upsert. 동작 원리는 LocationRankingJpaRepository.upsertScore와 같다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            insert into user_ranking (stage_id, user_id, user_score, updated_at)
            values (:stageId, :userId, :delta, :now)
            on conflict (stage_id, user_id)
            do update set user_score = user_ranking.user_score + :delta,
                          updated_at = :now
            """, nativeQuery = true)
    void upsertScore(@Param("stageId") Long stageId,
                     @Param("userId") UUID userId,
                     @Param("delta") long delta,
                     @Param("now") LocalDateTime now);

    /**
     * 라운드 개인 랭킹 상위 limit명. 게시물을 올린 유저만 행이 있어 미참여자는 나오지 않는다.
     * 동점은 rank() 기본 동작대로 같은 등수로 매기고 다음 등수는 건너뛴다.
     * 탈퇴한 유저는 행과 점수는 남기되 rank() 대상에서 빼서, 탈퇴로 순위 번호에 구멍이 생기지 않게 한다.
     */
    @Query(value = """
            select u.user_id as userId,
                   u.user_name as userName,
                   cast(ur.user_score as bigint) as userScore,
                   cast(rank() over (order by ur.user_score desc) as int) as userRank
              from user_ranking ur
              join users u on u.user_id = ur.user_id
             where ur.stage_id = :stageId
               and u.status <> 'WITHDRAWN'
             order by userRank, u.user_name
             limit :limit
            """, nativeQuery = true)
    List<UserRankRow> findTopRanking(@Param("stageId") Long stageId, @Param("limit") int limit);

    /**
     * findTopRanking에서 viewerId가 차단한 유저를 뺀 것.
     * 차단 조건을 rank()와 같은 where에 넣으면 순위가 당겨지므로, 서브쿼리에서 전체 기준 등수를 먼저 매기고
     * 바깥에서 차단 유저를 거른 뒤 limit을 건다. 남은 유저의 등수는 원래 값 그대로이고, 빠진 만큼 아래에서 채워진다.
     * 탈퇴 유저는 차단과 달리 모두에게서 영구히 빠지므로 findTopRanking과 같게 서브쿼리 안에서 rank() 전에 거른다.
     */
    @Query(value = """
            select t.userId, t.userName, t.userScore, t.userRank
              from (select u.user_id as userId,
                           u.user_name as userName,
                           cast(ur.user_score as bigint) as userScore,
                           cast(rank() over (order by ur.user_score desc) as int) as userRank
                      from user_ranking ur
                      join users u on u.user_id = ur.user_id
                     where ur.stage_id = :stageId
                       and u.status <> 'WITHDRAWN') t
             where not exists (select 1
                                 from user_block b
                                where b.blocker_id = :viewerId
                                  and b.blocked_id = t.userId)
             order by t.userRank, t.userName
             limit :limit
            """, nativeQuery = true)
    List<UserRankRow> findTopRankingExcludingBlockedBy(@Param("stageId") Long stageId,
                                                       @Param("viewerId") UUID viewerId,
                                                       @Param("limit") int limit);

    /**
     * 본인 순위 조회. 서브쿼리로 전체 등수를 매긴 뒤 바깥에서 한 명을 골라낸다.
     * 상위 목록에서 찾는 방식이면 목록 밖 유저의 순위를 알 수 없다.
     */
    @Query(value = """
            select t.userId, t.userName, t.userScore, t.userRank
              from (select u.user_id as userId,
                           u.user_name as userName,
                           cast(ur.user_score as bigint) as userScore,
                           cast(rank() over (order by ur.user_score desc) as int) as userRank
                      from user_ranking ur
                      join users u on u.user_id = ur.user_id
                     where ur.stage_id = :stageId) t
             where t.userId = :userId
            """, nativeQuery = true)
    Optional<UserRankRow> findMyRanking(@Param("stageId") Long stageId, @Param("userId") UUID userId);
}
