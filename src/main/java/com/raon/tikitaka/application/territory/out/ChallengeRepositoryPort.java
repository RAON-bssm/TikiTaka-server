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
}
