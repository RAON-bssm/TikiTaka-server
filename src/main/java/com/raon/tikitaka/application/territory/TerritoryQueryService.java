package com.raon.tikitaka.application.territory;

import com.raon.tikitaka.application.territory.in.QueryTerritoryUseCase;
import com.raon.tikitaka.application.territory.out.TerritoryCycleRepositoryPort;
import com.raon.tikitaka.application.territory.out.TerritoryQueryPort;
import com.raon.tikitaka.application.territory.view.CurrentCycleView;
import com.raon.tikitaka.application.territory.view.RepresentativesView;
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

    /**
     * 대표 선발 결과. 임시 API, 프론트 확정 후 변경될 수 있음.
     * cycleId가 없고 진행 중인 사이클도 없으면 동 목록 조회(getDongs)와 같게 빈 결과를 준다.
     * 선발 전이면 빈 목록이다(404가 아니다). 탈퇴 유저와 내가 차단한 유저는 빼고, 순위는 선발 시점 값을 그대로 둔다.
     */
    @Override
    public RepresentativesView getRepresentatives(UUID userId, Long cycleId, Long locationId) {
        TerritoryCycle cycle = cycleId == null
                ? cycleRepositoryPort.findCurrent(LocalDateTime.now()).orElse(null)
                : cycleRepositoryPort.findById(cycleId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사이클을 찾을 수 없어요."));
        if (cycle == null) {
            return new RepresentativesView(null, false, List.of());
        }
        if (!cycle.isRepresentativesSelected()) {
            return new RepresentativesView(cycle.getCycleId(), false, List.of());
        }
        List<RepresentativesView.Item> items = territoryQueryPort
                .findRepresentatives(cycle.getCycleId(), locationId, userId).stream()
                .map(r -> new RepresentativesView.Item(r.getRank(), r.getUser().getUserId(), r.getUser().getUserName(),
                        r.getScore(), r.getLocation().getLocationId(), r.getAssignedDong().getDongId(),
                        r.getAssignedDong().getDongCode(), r.getAssignedDong().getDongName(), r.getAssignedBy(),
                        r.getUser().getUserId().equals(userId)))
                .toList();
        return new RepresentativesView(cycle.getCycleId(), true, items);
    }

    private Long myLocationId(UUID userId) {
        return userRepositoryPort.findByIdWithLocations(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "유저를 찾을 수 없습니다."))
                .getMainLocation().getLocationId();
    }
}
