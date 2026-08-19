package com.raon.tikitaka.adapter.match.out;

import com.raon.tikitaka.application.match.out.StageRepositoryPort;
import com.raon.tikitaka.domain.match.Stage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class StagePersistenceAdapter implements StageRepositoryPort {

    private final StageJpaRepository stageJpaRepository;

    @Override
    public Optional<Stage> findLatestByEndedAt() {
        return stageJpaRepository.findTopByOrderByEndedAtDesc();
    }

    @Override
    public Stage save(Stage stage) {
        return stageJpaRepository.save(stage);
    }

    @Override
    public Optional<Stage> findById(Long stageId) {
        return stageJpaRepository.findById(stageId);
    }

    @Override
    public List<Stage> findUpcomingWithoutMatch(LocalDateTime now, LocalDateTime threshold) {
        return stageJpaRepository.findUpcomingWithoutMatch(now, threshold);
    }

    @Override
    public Optional<Stage> findCurrent(LocalDateTime now) {
        return stageJpaRepository.findCurrent(now);
    }

    @Override
    public List<Stage> findAll() {
        return stageJpaRepository.findAll();
    }
}
