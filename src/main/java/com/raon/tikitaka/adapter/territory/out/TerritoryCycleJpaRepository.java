package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.domain.territory.TerritoryCycle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TerritoryCycleJpaRepository extends JpaRepository<TerritoryCycle, Long> {

    @Query("select c from TerritoryCycle c where c.pointStage.stageId = :stageId or c.battleStage.stageId = :stageId")
    Optional<TerritoryCycle> findByStageId(@Param("stageId") Long stageId);

    boolean existsByPointStage_StageId(Long pointStageId);
}
