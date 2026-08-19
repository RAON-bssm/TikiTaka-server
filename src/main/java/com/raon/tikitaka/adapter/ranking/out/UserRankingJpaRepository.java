package com.raon.tikitaka.adapter.ranking.out;

import com.raon.tikitaka.domain.ranking.UserRanking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface UserRankingJpaRepository extends JpaRepository<UserRanking, Long> {

    /**
     * 개인 점수 누적 UPSERT — 동작 원리는 LocationRankingJpaRepository.upsertScore와 동일.
     * (uk_user_ranking_stage_user 유니크 제약이 conflict 기준)
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
}
