package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.adapter.ranking.out.RankingPersistenceAdapter;
import com.raon.tikitaka.application.ranking.LocationRankRow;
import com.raon.tikitaka.application.ranking.SeasonRankRow;
import com.raon.tikitaka.application.ranking.UserRankRow;
import com.raon.tikitaka.application.ranking.out.RankingRepositoryPort;
import com.raon.tikitaka.application.territory.in.CheckRankingFrozenUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * [데코레이터] RankingRepositoryPort의 기본 구현(RankingPersistenceAdapter)을 감싼다. @Primary라서
 * RankingRepositoryPort를 주입받는 모든 곳(현재 PostService 등)은 이 클래스를 받는다.
 *
 * 땅따먹기 랭킹 동결: 동결된 라운드(포인트 라운드 4일차 종료 이후, 땅따먹기 라운드 전체)에는
 * 개인·동네 점수 가산/차감을 건너뛴다. 게시물 포인트 적립은 PostService가 따로 하므로 그대로 쌓인다.
 * 동결 뒤 삭제된 글도 점수를 되돌리지 않는다. "동결된 랭킹은 어떤 변경도 받지 않는다".
 * 조회 메서드는 그대로 위임한다.
 */
@Primary
@Component
@RequiredArgsConstructor
public class RankingFreezeAdapter implements RankingRepositoryPort {

    private final RankingPersistenceAdapter delegate;
    private final CheckRankingFrozenUseCase checkRankingFrozenUseCase;

    @Override
    public void addLocationScore(Long stageId, Long locationId, long delta) {
        if (!checkRankingFrozenUseCase.isRankingFrozen(stageId, LocalDateTime.now())) {
            delegate.addLocationScore(stageId, locationId, delta);
        }
    }

    @Override
    public void addUserScore(Long stageId, UUID userId, long delta) {
        if (!checkRankingFrozenUseCase.isRankingFrozen(stageId, LocalDateTime.now())) {
            delegate.addUserScore(stageId, userId, delta);
        }
    }

    @Override
    public List<SeasonRankRow> calculateSeasonRanking(int season) {
        return delegate.calculateSeasonRanking(season);
    }

    @Override
    public List<LocationRankRow> findLocationRanking(Long stageId) {
        return delegate.findLocationRanking(stageId);
    }

    @Override
    public List<UserRankRow> findTopUserRanking(Long stageId, int limit) {
        return delegate.findTopUserRanking(stageId, limit);
    }

    @Override
    public List<UserRankRow> findTopUserRankingExcludingBlockedBy(Long stageId, UUID viewerId, int limit) {
        return delegate.findTopUserRankingExcludingBlockedBy(stageId, viewerId, limit);
    }

    @Override
    public Optional<UserRankRow> findMyUserRanking(Long stageId, UUID userId) {
        return delegate.findMyUserRanking(stageId, userId);
    }
}
