package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.domain.territory.TeamLandResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TeamLandResultJpaRepository extends JpaRepository<TeamLandResult, Long> {

    @Query("select r from TeamLandResult r join fetch r.location where r.cycle.cycleId = :cycleId order by r.rank, r.location.locationId")
    List<TeamLandResult> findAllByCycleId(@Param("cycleId") Long cycleId);
}
