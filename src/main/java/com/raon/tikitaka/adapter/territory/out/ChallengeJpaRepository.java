package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.domain.territory.Challenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ChallengeJpaRepository extends JpaRepository<Challenge, UUID> {

    @Query("select c from Challenge c join fetch c.territory t join fetch t.dong where c.challengeId = :id")
    Optional<Challenge> findWithTerritoryById(@Param("id") UUID challengeId);

    interface AttackCount {
        Long getTerritoryId();
        Long getAttacks();
    }

    /** 같은 사람이 하루에 여러 번 도전해도 1로 센다: (땅, 공격자, 날짜)로 중복을 지운 뒤 땅별로 센다 */
    @Query(value = """
            SELECT x.territory_id AS territoryId, COUNT(*) AS attacks
            FROM (SELECT DISTINCT c.territory_id, c.challenger_id, c.attack_date
                  FROM challenge c JOIN territory t ON t.territory_id = c.territory_id
                  WHERE t.cycle_id = :cycleId AND c.attack_date >= :since) x
            GROUP BY x.territory_id
            """, nativeQuery = true)
    java.util.List<AttackCount> countAttacks(@Param("cycleId") Long cycleId, @Param("since") java.time.LocalDate since);
}
