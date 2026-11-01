package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.domain.territory.LandDevelopment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.UUID;

public interface LandDevelopmentJpaRepository extends JpaRepository<LandDevelopment, Long> {

    long countByUser_UserIdAndCreatedAtGreaterThanEqual(UUID userId, LocalDateTime from);

    long countByTerritory_TerritoryIdAndSuccessTrueAndCreatedAtGreaterThanEqual(Long territoryId, LocalDateTime from);
}
