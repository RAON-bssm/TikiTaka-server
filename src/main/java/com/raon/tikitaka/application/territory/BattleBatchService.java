package com.raon.tikitaka.application.territory;

import com.raon.tikitaka.application.territory.in.RunBattleBatchUseCase;
import com.raon.tikitaka.application.territory.out.*;
import com.raon.tikitaka.application.territory.policy.LandPricePolicy;
import com.raon.tikitaka.domain.enums.RoundType;
import com.raon.tikitaka.domain.territory.DefaultMission;
import com.raon.tikitaka.domain.territory.Territory;
import com.raon.tikitaka.domain.territory.TerritoryCycle;
import com.raon.tikitaka.global.config.TerritoryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class BattleBatchService implements RunBattleBatchUseCase {

    private final TerritoryCycleRepositoryPort cycleRepositoryPort;
    private final TerritoryQueryPort territoryQueryPort;
    private final ChallengeRepositoryPort challengeRepositoryPort;
    private final DefaultMissionPort defaultMissionPort;
    private final RandomPort randomPort;
    private final LandPricePolicy landPricePolicy;
    private final TerritoryProperties properties;

    /**
     * 주인 없는 땅과 대표가 세팅하지 않은 땅에 기본 미션을 랜덤으로 넣는다. 방어 점수는 기준 점수(60), 대표 자리는 유지.
     * 사이클마다 defaults_applied로 한 번만 처리한다.
     */
    @Override
    public void applyDefaultMissions() {
        LocalDateTime now = LocalDateTime.now();
        for (TerritoryCycle cycle : cycleRepositoryPort.findAllAwaitingDefaults(now)) {
            List<DefaultMission> missions = defaultMissionPort.findAll();
            if (missions.isEmpty()) {
                // 미션 없이 시작하면 채점이 깨지므로 처리하지 않고 다음 실행에서 다시 시도한다
                log.error("기본 미션이 없습니다. seed/territory_default_mission.sql을 적용하세요. cycleId={}", cycle.getCycleId());
                return;
            }
            int applied = 0;
            for (Territory t : territoryQueryPort.findAllByCycleId(cycle.getCycleId())) {
                if (t.getMissionAdjective() == null) {
                    DefaultMission m = missions.get((int) (randomPort.nextDouble() * missions.size()));
                    t.applyDefaultIfNotSetUp(m.getAdjective(), m.getNoun(), m.getScoringCriteria(),
                            properties.defense().unownedMinScore());
                    applied++;
                }
            }
            cycle.markDefaultsApplied();
            log.info("기본 미션 적용 완료. cycleId={}, 땅 {}개", cycle.getCycleId(), applied);
        }
    }

    /**
     * 기본 땅값 = 2,500 × clamp(그 땅의 공격 수 ÷ 보드 평균, 0.5, 3.0).
     * 공격 수 = 최근 windowDays일의 (공격자, 날짜) 쌍. 보드 평균 = 전체 공격 수 ÷ 사이클의 땅 수(공격 0인 땅 포함).
     * 매번 처음부터 다시 계산하므로 여러 번 돌려도 같다.
     */
    @Override
    public void recalculateLandPrices() {
        LocalDateTime now = LocalDateTime.now();
        Optional<TerritoryCycle> current = battleCycle(now);
        if (current.isEmpty()) {
            return;
        }
        TerritoryCycle cycle = current.get();
        List<Territory> territories = territoryQueryPort.findAllByCycleId(cycle.getCycleId());
        if (territories.isEmpty()) {
            return;
        }
        Map<Long, Long> attacks = challengeRepositoryPort.countAttacksByTerritory(cycle.getCycleId(),
                now.toLocalDate().minusDays(properties.landPrice().windowDays()));
        double boardAverage = attacks.values().stream().mapToLong(Long::longValue).sum() / (double) territories.size();
        for (Territory t : territories) {
            t.updateBaseLandPrice(landPricePolicy.basePrice(attacks.getOrDefault(t.getTerritoryId(), 0L), boardAverage));
        }
        log.info("땅값 재계산 완료. cycleId={}, 보드 평균 공격 수 {}", cycle.getCycleId(), boardAverage);
    }

    @Override
    public void decayDefense() {
        LocalDateTime now = LocalDateTime.now();
        Optional<TerritoryCycle> current = battleCycle(now);
        if (current.isEmpty()) {
            return;
        }
        TerritoryCycle cycle = current.get();
        TerritoryProperties.Defense d = properties.defense();
        long decayed = territoryQueryPort.findAllByCycleId(cycle.getCycleId()).stream()
                .filter(t -> t.decayIfIdle(now, cycle.getBattleStage().getStartedAt(), d.idleDays(), d.decayPerDay(), d.decayFloor()))
                .count();
        log.info("방어 점수 감쇠 완료. cycleId={}, 땅 {}개", cycle.getCycleId(), decayed);
    }

    private Optional<TerritoryCycle> battleCycle(LocalDateTime now) {
        return cycleRepositoryPort.findCurrent(now).filter(c -> c.roundTypeAt(now) == RoundType.BATTLE);
    }
}
