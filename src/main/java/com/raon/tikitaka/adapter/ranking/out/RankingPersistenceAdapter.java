package com.raon.tikitaka.adapter.ranking.out;

import com.raon.tikitaka.application.ranking.LocationRankRow;
import com.raon.tikitaka.application.ranking.SeasonRankRow;
import com.raon.tikitaka.application.ranking.UserRankRow;
import com.raon.tikitaka.application.ranking.out.RankingRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RankingPersistenceAdapter implements RankingRepositoryPort {

    private final LocationRankingJpaRepository locationRankingJpaRepository;
    private final UserRankingJpaRepository userRankingJpaRepository;

    @Override
    public void addLocationScore(Long stageId, Long locationId, long delta) {
        locationRankingJpaRepository.upsertScore(stageId, locationId, delta, LocalDateTime.now());
    }

    @Override
    public void addUserScore(Long stageId, UUID userId, long delta) {
        userRankingJpaRepository.upsertScore(stageId, userId, delta, LocalDateTime.now());
    }

    @Override
    public List<SeasonRankRow> calculateSeasonRanking(int season) {
        return locationRankingJpaRepository.calculateSeasonRanking(season);
    }

    @Override
    public List<LocationRankRow> findLocationRanking(Long stageId) {
        return locationRankingJpaRepository.findLocationRanking(stageId);
    }

    @Override
    public List<UserRankRow> findTopUserRanking(Long stageId, int limit) {
        return userRankingJpaRepository.findTopRanking(stageId, limit);
    }

    @Override
    public Optional<UserRankRow> findMyUserRanking(Long stageId, UUID userId) {
        return userRankingJpaRepository.findMyRanking(stageId, userId);
    }
}
