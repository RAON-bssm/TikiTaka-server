package com.raon.tikitaka.application.territory;

import com.raon.tikitaka.application.territory.in.QueryTerritoryUseCase;
import com.raon.tikitaka.application.territory.out.TerritoryCycleRepositoryPort;
import com.raon.tikitaka.application.territory.out.TerritoryQueryPort;
import com.raon.tikitaka.application.territory.policy.DevelopmentPolicy;
import com.raon.tikitaka.application.territory.policy.TollPolicy;
import com.raon.tikitaka.application.territory.view.CurrentCycleView;
import com.raon.tikitaka.application.territory.view.TerritoryDetailView;
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
    private final TollPolicy tollPolicy;
    private final DevelopmentPolicy developmentPolicy;

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
        RoundType round = cycle.roundTypeAt(now);
        Long myLocationId = myLocationId(userId);
        List<Territory> territories = locationId == null
                ? territoryQueryPort.findAllByCycleId(cycle.getCycleId())
                : territoryQueryPort.findAllByCycleIdAndLocationId(cycle.getCycleId(), locationId);

        List<TerritoryDongsView.Item> items = territories.stream().map(t -> new TerritoryDongsView.Item(
                t.getTerritoryId(), t.getDong().getDongId(), t.getDong().getDongCode(), t.getDong().getDongName(),
                t.getDong().getLocation().getLocationId(),
                t.isOwned() ? t.getOwner().getUserId() : null,
                t.isOwned() ? t.getOwner().getUserName() : null,
                t.isOwned() ? t.getOwnerLocation().getLocationId() : null,
                t.getDefenseScore(), t.landPrice(), t.isOwnedBy(userId),
                challengeable(t, round, userId, myLocationId))).toList();
        return new TerritoryDongsView(cycle.getCycleId(), round, items);
    }

    @Override
    public TerritoryDetailView getDongDetail(UUID userId, Long dongCode) {
        TerritoryCycle cycle = cycleRepositoryPort.findCurrent(LocalDateTime.now())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "진행 중인 사이클이 없습니다."));
        // 대표 선발 전이거나 없는 동 코드면 404
        Territory t = territoryQueryPort.findByCycleIdAndDongCode(cycle.getCycleId(), dongCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "이 동의 땅이 아직 없습니다."));
        RoundType round = t.getCycle().roundTypeAt(LocalDateTime.now());
        boolean mine = t.isOwnedBy(userId);
        int landPrice = t.landPrice();
        return new TerritoryDetailView(t.getTerritoryId(), t.getDong().getDongCode(), t.getDong().getDongName(),
                t.getDong().getLocation().getLocationId(),
                t.isOwned() ? t.getOwner().getUserId() : null,
                t.isOwned() ? t.getOwner().getUserName() : null,
                t.isOwned() ? t.getOwnerLocation().getLocationId() : null,
                t.getMission(), t.getMissionAdjective(), t.getMissionNoun(), t.getScoringCriteria(),
                mine ? t.getPendingAdjective() : null, mine ? t.getPendingNoun() : null,
                mine ? t.getPendingCriteria() : null,
                t.getDefensePhoto(), t.getDefenseScore(), landPrice, tollPolicy.toll(landPrice),
                developmentPolicy.cost(landPrice), developmentPolicy.probability(),
                mine, challengeable(t, round, userId, myLocationId(userId)));
    }

    /** 땅따먹기 라운드이고, 내 땅도 우리 팀 땅도 아니면 도전할 수 있다 */
    private boolean challengeable(Territory t, RoundType round, UUID userId, Long myLocationId) {
        return round == RoundType.BATTLE && t.isChallengeableBy(userId, myLocationId);
    }

    private Long myLocationId(UUID userId) {
        return userRepositoryPort.findByIdWithLocations(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "유저를 찾을 수 없습니다."))
                .getMainLocation().getLocationId();
    }
}
