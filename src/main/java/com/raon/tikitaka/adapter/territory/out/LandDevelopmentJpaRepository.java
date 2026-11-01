package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.domain.territory.LandDevelopment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface LandDevelopmentJpaRepository extends JpaRepository<LandDevelopment, Long> {

    long countByUser_UserIdAndCreatedAtGreaterThanEqual(UUID userId, LocalDateTime from);

    long countByTerritory_TerritoryIdAndSuccessTrueAndCreatedAtGreaterThanEqual(Long territoryId, LocalDateTime from);

    @Query("select d from LandDevelopment d join fetch d.user " +
            "where d.territory.territoryId = :territoryId order by d.createdAt desc, d.id desc")
    List<LandDevelopment> findRecentByTerritory(
            @Param("territoryId") Long territoryId,
            Pageable pageable);
}
