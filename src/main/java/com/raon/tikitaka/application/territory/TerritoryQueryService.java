package com.raon.tikitaka.application.territory;

import com.raon.tikitaka.application.territory.in.QueryTerritoryUseCase;
import com.raon.tikitaka.application.territory.out.TerritoryCycleRepositoryPort;
import com.raon.tikitaka.application.territory.out.TerritoryQueryPort;
import com.raon.tikitaka.application.territory.view.CurrentCycleView;
import com.raon.tikitaka.application.territory.view.TerritoryDongsView;
import com.raon.tikitaka.application.user.out.UserRepositoryPort;
import com.raon.tikitaka.domain.enums.RoundType;
import com.raon.tikitaka.domain.territory.Territory;
import com.raon.tikitaka.domain.territory.TerritoryCycle;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TerritoryQueryService implements QueryTerritoryUseCase {

    private final TerritoryCycleRepositoryPort cycleRepositoryPort;
    private final TerritoryQueryPort territoryQueryPort;
    private final UserRepositoryPort userRepositoryPort;

    @Override
    public CurrentCycleView getCurrentCycle(UUID userId) {
        LocalDateTime now = LocalDateTime.now();
        Long myLocationId = myLocationId(userId);
        return cycleRepositoryPort.findCurrent(now)
                .map(c -> new CurrentCycleView(c.getCycleId(), c.roundTypeAt(now),
                        c.getPointStage().getStartedAt(), c.getRankingFreezeAt(),
                        c.getBattleStage().getStartedAt(), c.getBattleStage().getEndedAt(),
                        c.isRepresentativesSelected(), myLocationId))
                .orElse(new CurrentCycleView(null, RoundType.NONE, null, null, null, null, false, myLocationId));
    }

    /** 대표 선발(5일차) 전에는 땅이 없어서 빈 목록이다 */
    @Override
    public TerritoryDongsView getDongs(UUID userId, Long locationId) {
        LocalDateTime now = LocalDateTime.now();
        TerritoryCycle cycle = cycleRepositoryPort.findCurrent(now).orElse(null);
        if (cycle == null) {
            return new TerritoryDongsView(null, RoundType.NONE, List.of());
        }
        List<Territory> territories = locationId == null
                ? territoryQueryPort.findAllByCycleId(cycle.getCycleId())
                : territoryQueryPort.findAllByCycleIdAndLocationId(cycle.getCycleId(), locationId);

        List<TerritoryDongsView.Item> items = territories.stream().map(t -> new TerritoryDongsView.Item(
                t.getTerritoryId(), t.getDong().getDongId(), t.getDong().getDongCode(), t.getDong().getDongName(),
                t.getDong().getLocation().getLocationId(),
                t.isOwned() ? t.getOwner().getUserId() : null,
                t.isOwned() ? t.getOwner().getUserName() : null,
                t.isOwned() ? t.getOwnerLocation().getLocationId() : null,
                t.getDefenseScore(), t.landPrice(), t.isOwnedBy(userId))).toList();
        return new TerritoryDongsView(cycle.getCycleId(), cycle.roundTypeAt(now), items);
    }

    private Long myLocationId(UUID userId) {
        return userRepositoryPort.findByIdWithLocations(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "유저를 찾을 수 없습니다."))
                .getMainLocation().getLocationId();
    }
}
