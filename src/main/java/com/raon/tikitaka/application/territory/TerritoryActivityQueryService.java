package com.raon.tikitaka.application.territory;

import com.raon.tikitaka.application.territory.in.QueryTerritoryActivityUseCase;
import com.raon.tikitaka.application.territory.out.BlockedUserPort;
import com.raon.tikitaka.application.territory.out.LandDevelopmentPort;
import com.raon.tikitaka.application.territory.out.TerritoryCycleRepositoryPort;
import com.raon.tikitaka.application.territory.out.TerritoryQueryPort;
import com.raon.tikitaka.application.territory.view.DevelopmentHistoryView;
import com.raon.tikitaka.application.territory.view.MyTerritoriesView;
import com.raon.tikitaka.domain.enums.RoundType;
import com.raon.tikitaka.domain.territory.Territory;
import com.raon.tikitaka.domain.territory.TerritoryCycle;
import com.raon.tikitaka.global.config.TerritoryProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TerritoryActivityQueryService implements QueryTerritoryActivityUseCase {

    private static final int DEVELOPMENT_LIMIT = 100;

    private final TerritoryCycleRepositoryPort cycleRepositoryPort;
    private final TerritoryQueryPort territoryQueryPort;
    private final LandDevelopmentPort landDevelopmentPort;
    private final BlockedUserPort blockedUserPort;
    private final TerritoryProperties properties;

    /** 사이클이 없거나 대표 선발 전이면 빈 목록이다 */
    @Override
    public MyTerritoriesView getMyTerritories(UUID userId) {
        LocalDateTime now = LocalDateTime.now();
        TerritoryCycle cycle = cycleRepositoryPort.findCurrent(now).orElse(null);
        if (cycle == null) {
            return new MyTerritoriesView(null, RoundType.NONE, properties.maxOwned(), List.of());
        }
        List<MyTerritoriesView.Item> items = territoryQueryPort.findAllOwnedBy(cycle.getCycleId(), userId).stream()
                .map(t -> new MyTerritoriesView.Item(t.getTerritoryId(), t.getDong().getDongCode(),
                        t.getDong().getDongName(), t.getDong().getLocation().getLocationId(),
                        t.getMission(), t.getMissionAdjective(), t.getMissionNoun(), t.getScoringCriteria(),
                        t.getPendingAdjective(), t.getPendingNoun(), t.getPendingCriteria(),
                        t.getDefensePhoto(), t.getDefenseScore(), t.landPrice(), t.getDevelopmentValue(),
                        t.getOwnerLastActiveAt()))
                .toList();
        return new MyTerritoriesView(cycle.getCycleId(), cycle.roundTypeAt(now), properties.maxOwned(), items);
    }

    @Override
    public DevelopmentHistoryView getDevelopments(UUID userId, Long dongCode) {
        TerritoryCycle cycle = cycleRepositoryPort.findCurrent(LocalDateTime.now())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "진행 중인 사이클이 없습니다."));
        Territory t = territoryQueryPort.findByCycleIdAndDongCode(cycle.getCycleId(), dongCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "이 동의 땅이 아직 없습니다."));
        Set<UUID> blockedIds = blockedUserPort.findBlockedUserIds(userId);
        List<DevelopmentHistoryView.Item> items = landDevelopmentPort
                .findRecentByTerritory(t.getTerritoryId(), DEVELOPMENT_LIMIT).stream()
                .map(d -> {
                    UUID developerId = d.getUser().getUserId();
                    boolean blocked = blockedIds.contains(developerId);
                    return new DevelopmentHistoryView.Item(d.getId(), developerId,
                            blocked ? null : d.getUser().getUserName(), d.isSuccess(), d.getCost(),
                            d.getValueAdded(), d.getCreatedAt(), developerId.equals(userId), blocked);
                })
                .toList();
        return new DevelopmentHistoryView(dongCode, t.getDong().getDongName(), items);
    }
}
