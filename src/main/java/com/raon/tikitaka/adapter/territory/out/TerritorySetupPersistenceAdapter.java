package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.application.territory.out.RepresentativeCandidatePort;
import com.raon.tikitaka.application.territory.out.TerritorySetupPort;
import com.raon.tikitaka.domain.territory.Representative;
import com.raon.tikitaka.domain.territory.Territory;
import com.raon.tikitaka.domain.territory.TerritoryHistory;
import com.raon.tikitaka.domain.user.Users;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TerritorySetupPersistenceAdapter implements TerritorySetupPort, RepresentativeCandidatePort {

    private final TerritoryUserJpaRepository userJpaRepository;
    private final TerritoryJpaRepository territoryJpaRepository;
    private final RepresentativeJpaRepository representativeJpaRepository;
    private final TerritoryHistoryJpaRepository historyJpaRepository;
    private final RepresentativeCandidateJpaRepository candidateJpaRepository;
    private final TerritoryPostJpaRepository postJpaRepository;

    @Override
    public List<CandidateRow> findTopCandidates(Long stageId, Long locationId, int limit) {
        return candidateJpaRepository.findTop(stageId, locationId, limit).stream()
                .map(r -> new CandidateRow(r.getUserId(), r.getScore()))
                .toList();
    }

    @Override
    public long countPendingReviews(Long stageId, LocalDateTime writtenBefore) {
        return postJpaRepository.countPendingReviews(stageId, writtenBefore);
    }

    @Override
    public List<Users> findUsersByIds(Collection<UUID> userIds) {
        return userJpaRepository.findAllById(userIds);
    }

    @Override
    public List<Territory> saveTerritories(List<Territory> territories) {
        return territoryJpaRepository.saveAll(territories);
    }

    @Override
    public void saveRepresentatives(List<Representative> representatives) {
        representativeJpaRepository.saveAll(representatives);
    }

    @Override
    public void saveHistories(List<TerritoryHistory> histories) {
        historyJpaRepository.saveAll(histories);
    }
}
