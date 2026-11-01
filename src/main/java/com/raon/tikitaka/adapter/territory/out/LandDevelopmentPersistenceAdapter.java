package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.application.territory.out.LandDevelopmentPort;
import com.raon.tikitaka.domain.territory.LandDevelopment;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class LandDevelopmentPersistenceAdapter implements LandDevelopmentPort {

    private final LandDevelopmentJpaRepository repository;

    @Override
    public void save(LandDevelopment development) {
        repository.save(development);
    }

    @Override
    public long countByUserSince(UUID userId, LocalDateTime from) {
        return repository.countByUser_UserIdAndCreatedAtGreaterThanEqual(userId, from);
    }

    @Override
    public long countSuccessByTerritorySince(Long territoryId, LocalDateTime from) {
        return repository.countByTerritory_TerritoryIdAndSuccessTrueAndCreatedAtGreaterThanEqual(territoryId, from);
    }

    @Override
    public List<LandDevelopment> findRecentByTerritory(Long territoryId, int limit) {
        return repository.findRecentByTerritory(territoryId, PageRequest.of(0, limit));
    }
}
