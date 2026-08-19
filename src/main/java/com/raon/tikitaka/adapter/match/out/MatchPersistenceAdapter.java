package com.raon.tikitaka.adapter.match.out;

import com.raon.tikitaka.application.match.out.MatchRepositoryPort;
import com.raon.tikitaka.domain.enums.MatchType;
import com.raon.tikitaka.domain.match.Match;
import com.raon.tikitaka.domain.match.Stage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MatchPersistenceAdapter implements MatchRepositoryPort {

    private final MatchJpaRepository matchJpaRepository;

    @Override
    public Match save(Match match) {
        return matchJpaRepository.save(match);
    }

    @Override
    public boolean existsByStage(Stage stage) {
        return matchJpaRepository.existsByStage(stage);
    }

    @Override
    public List<Match> findAllByMatchType(MatchType matchType) {
        return matchJpaRepository.findAllByMatchType(matchType);
    }

    @Override
    public List<Match> findAllByStageEndedAt(LocalDateTime endedAt) {
        return matchJpaRepository.findAllByStageEndedAt(endedAt);
    }
}
