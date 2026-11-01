package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.application.territory.out.LandDevelopmentPort;
import com.raon.tikitaka.domain.territory.LandDevelopment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
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
}
