package com.raon.tikitaka.application.territory;

import com.raon.tikitaka.application.location.out.LocationRepositoryPort;
import com.raon.tikitaka.application.territory.in.SelectRepresentativesUseCase;
import com.raon.tikitaka.application.territory.out.*;
import com.raon.tikitaka.application.territory.out.RepresentativeCandidatePort.CandidateRow;
import com.raon.tikitaka.application.territory.policy.AssignmentPolicy;
import com.raon.tikitaka.application.territory.policy.AssignmentPolicy.Assignment;
import com.raon.tikitaka.application.territory.policy.AssignmentPolicy.Candidate;
import com.raon.tikitaka.application.territory.policy.LandPricePolicy;
import com.raon.tikitaka.domain.enums.TerritoryEventType;
import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.domain.territory.*;
import com.raon.tikitaka.domain.user.Users;
import com.raon.tikitaka.global.config.TerritoryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RepresentativeService implements SelectRepresentativesUseCase {

    private final TerritoryCycleRepositoryPort cycleRepositoryPort;
    private final LocationRepositoryPort locationRepositoryPort;
    private final DongRepositoryPort dongRepositoryPort;
    private final DongPreferenceRepositoryPort preferenceRepositoryPort;
    private final RepresentativeCandidatePort candidatePort;
    private final TerritorySetupPort setupPort;
    private final AssignmentPolicy assignmentPolicy;
    private final LandPricePolicy landPricePolicy;
    private final TerritoryProperties properties;

    /**
     * 5일차 05:40. 사이클 하나를 한 트랜잭션으로 처리한다: 중간에 실패하면 그 사이클은 통째로 롤백되고
     * representatives_selected가 false로 남아 다음 실행에서 다시 시도된다(멱등).
     */
    @Override
    @Transactional
    public void execute() {
        LocalDateTime now = LocalDateTime.now();
        for (TerritoryCycle cycle : cycleRepositoryPort.findAllAwaitingSelection(now)) {
            if (now.isAfter(cycle.getPointStage().getEndedAt())) {
                log.warn("대표 선발이 늦었습니다. 땅따먹기 라운드 시작 후 선발합니다. cycleId={}", cycle.getCycleId());
            }
            select(cycle, now);
        }
    }

    private void select(TerritoryCycle cycle, LocalDateTime now) {
        int unownedMin = properties.defense().unownedMinScore();
        int representativeCount = 0;

        for (Location location : locationRepositoryPort.findAll()) {
            List<Dong> dongs = dongRepositoryPort.findAllByLocationId(location.getLocationId());
            if (dongs.isEmpty()) {
                continue;   // 동 데이터가 없는 지역(부산 외)은 땅따먹기 대상이 아니다
            }

            // 1) 동마다 주인 없는 땅을 연다. 대표가 모자라면 남는 동은 이대로 시작한다
            Map<Long, Territory> territoryByDong = new HashMap<>();
            for (Dong dong : dongs) {
                territoryByDong.put(dong.getDongId(), Territory.open(cycle, dong, landPricePolicy.initialPrice(), unownedMin));
            }

            // 2) 이 구의 개인 랭킹 상위 (동 수)명
            List<CandidateRow> rows = candidatePort.findTopCandidates(
                    cycle.getPointStage().getStageId(), location.getLocationId(), dongs.size());
            if (!rows.isEmpty()) {
                representativeCount += assign(cycle, location, rows, territoryByDong, now);
            }
            // 3) 땅을 저장하고(id 발급) 배정된 땅의 이력을 남긴다
            List<TerritoryHistory> histories = new ArrayList<>();
            for (Territory t : setupPort.saveTerritories(new ArrayList<>(territoryByDong.values()))) {
                if (t.isOwned()) {
                    histories.add(TerritoryHistory.of(t.getTerritoryId(), null, t.getOwner().getUserId(), null,
                            TerritoryEventType.ASSIGNED, now));
                }
            }
            setupPort.saveHistories(histories);
        }

        cycle.markRepresentativesSelected();
        log.info("대표 선발 완료. cycleId={}, 대표 {}명", cycle.getCycleId(), representativeCount);
    }

    /** 희망 동으로 배치하고(땅에 주인 지정) 대표를 저장한다. 배치된 대표 수를 돌려준다 */
    private int assign(TerritoryCycle cycle, Location location, List<CandidateRow> rows,
                       Map<Long, Territory> territoryByDong, LocalDateTime now) {
        List<UUID> userIds = rows.stream().map(CandidateRow::userId).toList();
        Map<UUID, List<Long>> prefsByUser = preferenceRepositoryPort.findAllByUserIds(userIds).stream()
                .collect(Collectors.groupingBy(p -> p.getUser().getUserId(),
                        Collectors.mapping(p -> p.getDong().getDongId(), Collectors.toList())));
        Map<UUID, Users> users = setupPort.findUsersByIds(userIds).stream()
                .collect(Collectors.toMap(Users::getUserId, Function.identity()));
        Map<UUID, Long> scores = rows.stream().collect(Collectors.toMap(CandidateRow::userId, CandidateRow::score));

        List<Candidate> candidates = userIds.stream()
                // 본진 구의 동만 남긴다. 등록 시 검증하지만 배치 시점에도 한 번 더 거른다
                .map(id -> new Candidate(id, prefsByUser.getOrDefault(id, List.of()).stream()
                        .filter(territoryByDong::containsKey).toList()))
                .toList();

        List<Representative> representatives = new ArrayList<>();
        List<Assignment> assignments = assignmentPolicy.assign(candidates, territoryByDong.keySet());
        for (int i = 0; i < assignments.size(); i++) {
            Assignment a = assignments.get(i);
            Users user = users.get(a.userId());
            Territory territory = territoryByDong.get(a.dongId());
            territory.assign(user, location, now);
            representatives.add(Representative.of(cycle, user, location, i + 1, scores.get(a.userId()),
                    territory.getDong(), a.type()));
        }
        setupPort.saveRepresentatives(representatives);
        return assignments.size();
    }
}
