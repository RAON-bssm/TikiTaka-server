package com.raon.tikitaka.application.territory;

import com.raon.tikitaka.application.match.out.StageRepositoryPort;
import com.raon.tikitaka.application.territory.in.CheckRankingFrozenUseCase;
import com.raon.tikitaka.application.territory.in.EnsureTerritoryCyclesUseCase;
import com.raon.tikitaka.application.territory.out.TerritoryCycleRepositoryPort;
import com.raon.tikitaka.domain.match.Stage;
import com.raon.tikitaka.domain.territory.TerritoryCycle;
import com.raon.tikitaka.global.config.TerritoryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TerritoryCycleService implements EnsureTerritoryCyclesUseCase, CheckRankingFrozenUseCase {

    private final StageRepositoryPort stageRepositoryPort;
    private final TerritoryCycleRepositoryPort cycleRepositoryPort;
    private final TerritoryProperties territoryProperties;

    /**
     * 시즌 안에서 (1,2), (3,4) 라운드를 사이클로 묶는다. 이미 끝난 포인트 라운드는 묶지 않는다
     * (기능 출시 전 과거 라운드에 사이클이 생기는 것을 막는다). 여러 번 실행해도 결과가 같다.
     */
    @Override
    public void execute() {
        LocalDateTime now = LocalDateTime.now();
        List<Stage> stages = stageRepositoryPort.findAll();
        Map<String, Stage> byKey = stages.stream()
                .collect(Collectors.toMap(s -> key(s.getSeason(), s.getRound()), Function.identity()));

        int created = 0;
        for (Stage point : stages) {
            if (point.getRound() % 2 == 0 || !point.getEndedAt().isAfter(now)) {
                continue;
            }
            Stage battle = byKey.get(key(point.getSeason(), point.getRound() + 1));
            if (battle == null || cycleRepositoryPort.existsByPointStageId(point.getStageId())) {
                continue;   // 짝 라운드가 아직 없거나(시즌 마지막 홀수 라운드 포함) 이미 묶였다
            }
            cycleRepositoryPort.save(TerritoryCycle.create(point, battle, territoryProperties.rankingDays()));
            created++;
        }
        if (created > 0) {
            log.info("땅따먹기 사이클 {}개 생성", created);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isRankingFrozen(Long stageId, LocalDateTime at) {
        return cycleRepositoryPort.findByStageId(stageId)
                .map(cycle -> cycle.isRankingFrozen(stageId, at))
                .orElse(false);     // 사이클 밖의 라운드는 기존처럼 랭킹을 쌓는다
    }

    private static String key(int season, int round) {
        return season + ":" + round;
    }
}
