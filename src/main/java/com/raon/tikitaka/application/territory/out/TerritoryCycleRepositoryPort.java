package com.raon.tikitaka.application.territory.out;

import com.raon.tikitaka.domain.territory.TerritoryCycle;

import java.util.Optional;

public interface TerritoryCycleRepositoryPort {

    /** 포인트 라운드나 땅따먹기 라운드로 stageId를 가진 사이클 */
    Optional<TerritoryCycle> findByStageId(Long stageId);

    boolean existsByPointStageId(Long pointStageId);

    TerritoryCycle save(TerritoryCycle cycle);
}
