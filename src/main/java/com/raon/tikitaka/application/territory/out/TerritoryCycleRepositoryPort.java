package com.raon.tikitaka.application.territory.out;

import com.raon.tikitaka.domain.territory.TerritoryCycle;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TerritoryCycleRepositoryPort {

    /** 포인트 라운드나 땅따먹기 라운드로 stageId를 가진 사이클 */
    Optional<TerritoryCycle> findByStageId(Long stageId);

    boolean existsByPointStageId(Long pointStageId);

    TerritoryCycle save(TerritoryCycle cycle);

    /** 랭킹이 동결됐고 아직 대표를 뽑지 않았으며 땅따먹기 라운드가 끝나지 않은 사이클 */
    List<TerritoryCycle> findAllAwaitingSelection(LocalDateTime now);
}
