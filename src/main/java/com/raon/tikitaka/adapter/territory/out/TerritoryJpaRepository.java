package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.domain.territory.Territory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TerritoryJpaRepository extends JpaRepository<Territory, Long> {

    String FETCH = "select t from Territory t join fetch t.dong d left join fetch t.owner left join fetch t.ownerLocation ";

    @Query(FETCH + "where t.cycle.cycleId = :cycleId order by d.dongCode")
    List<Territory> findAllByCycleId(@Param("cycleId") Long cycleId);

    @Query(FETCH + "where t.cycle.cycleId = :cycleId and d.location.locationId = :locationId order by d.dongCode")
    List<Territory> findAllByCycleIdAndLocationId(@Param("cycleId") Long cycleId, @Param("locationId") Long locationId);

    @Query(FETCH + "join fetch t.cycle c join fetch c.pointStage join fetch c.battleStage " +
            "where c.cycleId = :cycleId and d.dongCode = :dongCode")
    Optional<Territory> findWithDetailsByCycleIdAndDongCode(@Param("cycleId") Long cycleId, @Param("dongCode") Long dongCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Territory t join fetch t.dong join fetch t.cycle c join fetch c.pointStage join fetch c.battleStage " +
            "where t.territoryId = :id")
    Optional<Territory> findByIdForUpdate(@Param("id") Long territoryId);

    @Query("select count(t) from Territory t where t.cycle.cycleId = :cycleId and t.owner.userId = :userId")
    long countOwnedBy(@Param("cycleId") Long cycleId, @Param("userId") UUID userId);

    @Query(FETCH + "join fetch t.cycle c join fetch c.pointStage join fetch c.battleStage where t.territoryId = :id")
    Optional<Territory> findWithDetailsById(@Param("id") Long territoryId);
}
