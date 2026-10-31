package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.application.territory.out.TerritoryQueryPort;
import com.raon.tikitaka.domain.territory.Territory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

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
}
