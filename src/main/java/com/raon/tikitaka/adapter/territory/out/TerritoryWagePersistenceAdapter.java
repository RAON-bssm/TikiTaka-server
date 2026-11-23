package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.application.territory.out.TerritoryWagePort;
import com.raon.tikitaka.domain.territory.TerritoryWage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TerritoryWagePersistenceAdapter implements TerritoryWagePort {

    private final TerritoryWageJpaRepository repository;

    @Override
    public void saveAll(List<TerritoryWage> wages) {
        repository.saveAll(wages);
    }

    @Override
    public List<TerritoryWage> findRecentByUser(UUID userId, int limit) {
        return repository.findRecentByUser(userId, PageRequest.of(0, limit));
    }

    @Override
    public List<TerritoryWage> findRecentByUserAndCycle(UUID userId, Long cycleId, int limit) {
        return repository.findRecentByUserAndCycle(userId, cycleId, PageRequest.of(0, limit));
    }

    @Override
    public List<UUID> findUserIdsPaidOn(LocalDate paidOn) {
        return repository.findUserIdsByPaidOn(paidOn);
    }
}
