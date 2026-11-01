package com.raon.tikitaka.application.territory;

import com.raon.tikitaka.application.location.out.LocationRepositoryPort;
import com.raon.tikitaka.application.territory.in.TeamRankingUseCase;
import com.raon.tikitaka.application.territory.out.TeamLandResultPort;
import com.raon.tikitaka.application.territory.out.TerritoryCycleRepositoryPort;
import com.raon.tikitaka.application.territory.out.TerritoryQueryPort;
import com.raon.tikitaka.application.territory.policy.TeamStandingCalculator;
import com.raon.tikitaka.application.territory.policy.TeamStandingCalculator.Standing;
import com.raon.tikitaka.application.territory.view.TeamRankingView;
import com.raon.tikitaka.application.user.out.UserRepositoryPort;
import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.domain.territory.TeamLandResult;
import com.raon.tikitaka.domain.territory.Territory;
import com.raon.tikitaka.domain.territory.TerritoryCycle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeamRankingService implements TeamRankingUseCase {

    private final TerritoryCycleRepositoryPort cycleRepositoryPort;
    private final TerritoryQueryPort territoryQueryPort;
    private final TeamLandResultPort resultPort;
    private final LocationRepositoryPort locationRepositoryPort;
    private final UserRepositoryPort userRepositoryPort;

    /** 확정된 사이클은 저장된 스냅샷을, 진행 중인 사이클은 지금 땅 상태로 계산한 값을 준다 */
    @Override
    @Transactional(readOnly = true)
    public TeamRankingView getRanking(UUID userId, Long cycleId) {
        TerritoryCycle cycle = resolveCycle(cycleId);
        Long myLocationId = userRepositoryPort.findByIdWithLocations(userId)
                .map(u -> u.getMainLocation().getLocationId()).orElse(null);
        if (cycle == null) {
            return new TeamRankingView(null, false, myLocationId, List.of());
        }

        if (cycle.isResultFinalized()) {
            List<TeamRankingView.Team> teams = resultPort.findAllByCycleId(cycle.getCycleId()).stream()
                    .map(r -> new TeamRankingView.Team(r.getRank(), r.getLocation().getLocationId(),
                            r.getLocation().getLocationName(), r.getTotalLandPrice(), r.getOwnedDongCount(),
                            r.getAvgLandPrice()))
                    .toList();
            return new TeamRankingView(cycle.getCycleId(), true, myLocationId, teams);
        }

        Map<Long, Location> locations = locationsById();
        List<TeamRankingView.Team> teams = standings(cycle).stream()
                .map(s -> new TeamRankingView.Team(s.rank(), s.locationId(), locations.get(s.locationId()).getLocationName(),
                        s.totalLandPrice(), s.ownedDongCount(), s.avgLandPrice()))
                .toList();
        return new TeamRankingView(cycle.getCycleId(), false, myLocationId, teams);
    }

    /**
     * 06:00 배치. 땅따먹기 라운드가 끝나면(보통 0시) 도전·개발·땅값·감쇠가 모두 멈추므로
     * 06:00의 땅 상태가 곧 종료 시점의 상태다. result_finalized로 사이클당 한 번만 저장한다.
     */
    @Override
    @Transactional
    public void finalizeEndedCycles() {
        Map<Long, Location> locations = null;
        for (TerritoryCycle cycle : cycleRepositoryPort.findAllAwaitingFinalize(LocalDateTime.now())) {
            if (locations == null) {
                locations = locationsById();
            }
            Map<Long, Location> byId = locations;
            List<TeamLandResult> results = standings(cycle).stream()
                    .map(s -> TeamLandResult.of(cycle, byId.get(s.locationId()), s.totalLandPrice(), s.ownedDongCount(), s.rank()))
                    .toList();
            resultPort.saveAll(results);
            cycle.markResultFinalized();
            log.info("땅따먹기 랭킹 확정. cycleId={}, 팀 {}개", cycle.getCycleId(), results.size());
        }
    }

    /** 팀 = 이 사이클 보드에 동이 있는 구 전체(땅이 0개여도 0점으로 포함) */
    private List<Standing> standings(TerritoryCycle cycle) {
        List<Territory> territories = territoryQueryPort.findAllByCycleId(cycle.getCycleId());
        Set<Long> teamIds = territories.stream()
                .map(t -> t.getDong().getLocation().getLocationId())
                .collect(Collectors.toSet());
        return TeamStandingCalculator.calculate(territories, teamIds);
    }

    private TerritoryCycle resolveCycle(Long cycleId) {
        if (cycleId != null) {
            return cycleRepositoryPort.findById(cycleId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사이클을 찾을 수 없습니다."));
        }
        // 대표 선발 전(1~4일차)에는 땅이 없으므로 직전에 확정된 랭킹을 보여준다
        return cycleRepositoryPort.findCurrent(LocalDateTime.now())
                .filter(TerritoryCycle::isRepresentativesSelected)
                .or(cycleRepositoryPort::findLatestFinalized)
                .orElse(null);
    }

    private Map<Long, Location> locationsById() {
        return locationRepositoryPort.findAll().stream()
                .collect(Collectors.toMap(Location::getLocationId, Function.identity()));
    }
}
