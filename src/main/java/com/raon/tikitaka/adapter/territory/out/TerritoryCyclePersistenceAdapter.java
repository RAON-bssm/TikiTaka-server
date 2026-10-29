package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.application.territory.out.TerritoryCycleRepositoryPort;
import com.raon.tikitaka.domain.territory.TerritoryCycle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TerritoryCyclePersistenceAdapter implements TerritoryCycleRepositoryPort {

    private final TerritoryCycleJpaRepository territoryCycleJpaRepository;

    @Override
    public Optional<TerritoryCycle> findByStageId(Long stageId) {
        return territoryCycleJpaRepository.findByStageId(stageId);
    }

    @Override
    public boolean existsByPointStageId(Long pointStageId) {
        return territoryCycleJpaRepository.existsByPointStage_StageId(pointStageId);
    }

    @Override
    public TerritoryCycle save(TerritoryCycle cycle) {
        return territoryCycleJpaRepository.save(cycle);
    }
}
