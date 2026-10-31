package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.application.territory.out.ChallengeRepositoryPort;
import com.raon.tikitaka.domain.territory.Challenge;
import com.raon.tikitaka.domain.territory.TerritoryHistory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ChallengePersistenceAdapter implements ChallengeRepositoryPort {

    private final ChallengeJpaRepository challengeJpaRepository;
    private final TerritoryHistoryJpaRepository historyJpaRepository;

    @Override
    public Challenge save(Challenge challenge) {
        return challengeJpaRepository.save(challenge);
    }

    @Override
    public void saveHistory(TerritoryHistory history) {
        historyJpaRepository.save(history);
    }

    @Override
    public Optional<Challenge> findById(UUID challengeId) {
        return challengeJpaRepository.findWithTerritoryById(challengeId);
    }
}
