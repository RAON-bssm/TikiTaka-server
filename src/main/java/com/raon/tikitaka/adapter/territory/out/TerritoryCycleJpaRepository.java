package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.domain.territory.TerritoryCycle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TerritoryCycleJpaRepository extends JpaRepository<TerritoryCycle, Long> {

    @Query("select c from TerritoryCycle c where c.pointStage.stageId = :stageId or c.battleStage.stageId = :stageId")
    Optional<TerritoryCycle> findByStageId(@Param("stageId") Long stageId);

    boolean existsByPointStage_StageId(Long pointStageId);

    @Query("select c from TerritoryCycle c join fetch c.pointStage p join fetch c.battleStage b " +
            "where p.startedAt <= :now and b.endedAt > :now")
    Optional<TerritoryCycle> findCurrent(@Param("now") LocalDateTime now);

    @Query("select c from TerritoryCycle c join fetch c.pointStage join fetch c.battleStage " +
            "where c.representativesSelected = false and c.rankingFreezeAt <= :now and c.battleStage.endedAt > :now")
    List<TerritoryCycle> findAllAwaitingSelection(@Param("now") LocalDateTime now);
}
