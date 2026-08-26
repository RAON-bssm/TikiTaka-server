package com.raon.tikitaka.adapter.ranking.out;

import com.raon.tikitaka.application.ranking.out.SeasonResultRepositoryPort;
import com.raon.tikitaka.domain.ranking.SeasonLocationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SeasonResultPersistenceAdapter implements SeasonResultRepositoryPort {

    private final SeasonLocationResultJpaRepository seasonLocationResultJpaRepository;

    @Override
    public boolean existsBySeason(int season) {
        return seasonLocationResultJpaRepository.existsBySeason(season);
    }

    @Override
    public SeasonLocationResult save(SeasonLocationResult result) {
        return seasonLocationResultJpaRepository.save(result);
    }
}
