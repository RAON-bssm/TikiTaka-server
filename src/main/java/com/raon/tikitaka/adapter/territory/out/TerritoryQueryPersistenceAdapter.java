package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.application.territory.out.TerritoryQueryPort;
import com.raon.tikitaka.domain.territory.Territory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TerritoryQueryPersistenceAdapter implements TerritoryQueryPort {

    private final TerritoryJpaRepository territoryJpaRepository;

    @Override
    public List<Territory> findAllByCycleId(Long cycleId) {
        return territoryJpaRepository.findAllByCycleId(cycleId);
    }

    @Override
    public List<Territory> findAllByCycleIdAndLocationId(Long cycleId, Long locationId) {
        return territoryJpaRepository.findAllByCycleIdAndLocationId(cycleId, locationId);
    }

    @Override
    public Optional<Territory> findByCycleIdAndDongCode(Long cycleId, Long dongCode) {
        return territoryJpaRepository.findWithDetailsByCycleIdAndDongCode(cycleId, dongCode);
    }

    @Override
    public Optional<Territory> findByIdForUpdate(Long territoryId) {
        return territoryJpaRepository.findByIdForUpdate(territoryId);
    }

    @Override
    public long countOwnedBy(Long cycleId, java.util.UUID userId) {
        return territoryJpaRepository.countOwnedBy(cycleId, userId);
    }

    @Override
    public Optional<Territory> findById(Long territoryId) {
        return territoryJpaRepository.findWithDetailsById(territoryId);
    }
}
