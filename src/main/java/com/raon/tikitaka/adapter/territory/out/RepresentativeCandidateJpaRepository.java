package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.domain.ranking.UserRanking;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/** user_ranking 읽기 전용. 기존 랭킹 리포지토리는 건드리지 않는다 */
public interface RepresentativeCandidateJpaRepository extends Repository<UserRanking, Long> {

    interface Row {
        UUID getUserId();
        Long getScore();
    }

    @Query(value = """
            SELECT ur.user_id AS userId, ur.user_score AS score
            FROM user_ranking ur
            JOIN users u ON u.user_id = ur.user_id
            WHERE ur.stage_id = :stageId
              AND u.main_location_id = :locationId
              AND u.status = 'ACTIVE'
              AND ur.user_score > 0
            ORDER BY ur.user_score DESC, ur.updated_at ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<Row> findTop(@Param("stageId") Long stageId, @Param("locationId") Long locationId, @Param("limit") int limit);
}
