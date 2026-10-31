package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.application.territory.out.TerritoryCycleRepositoryPort;
import com.raon.tikitaka.domain.territory.TerritoryCycle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
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
    public List<TerritoryCycle> findAllAwaitingSelection(LocalDateTime now) {
        return territoryCycleJpaRepository.findAllAwaitingSelection(now);
    }

    @Override
    public Optional<TerritoryCycle> findCurrent(LocalDateTime now) {
        return territoryCycleJpaRepository.findCurrent(now);
    }

    @Override
    public Optional<TerritoryCycle> findById(Long cycleId) {
        return territoryCycleJpaRepository.findById(cycleId);
    }

    @Override
    public TerritoryCycle save(TerritoryCycle cycle) {
        return territoryCycleJpaRepository.save(cycle);
    }
}
