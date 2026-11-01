package com.raon.tikitaka.application.territory.out;

import com.raon.tikitaka.domain.territory.Challenge;
import com.raon.tikitaka.domain.territory.TerritoryHistory;

import java.util.Optional;
import java.util.UUID;

public interface ChallengeRepositoryPort {

    Challenge save(Challenge challenge);

    void saveHistory(TerritoryHistory history);

    /** 땅·동을 함께 조회 */
    Optional<Challenge> findById(UUID challengeId);

    /** 사이클의 땅별 공격 수 = since 이후 (공격자, 날짜) 쌍의 수. 공격이 없는 땅은 결과에 없다 */
    java.util.Map<Long, Long> countAttacksByTerritory(Long cycleId, java.time.LocalDate since);
}
