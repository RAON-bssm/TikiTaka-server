package com.raon.tikitaka.adapter.scheduler;

import com.raon.tikitaka.application.territory.in.EnsureTerritoryCyclesUseCase;
import com.raon.tikitaka.application.territory.in.RunBattleBatchUseCase;
import com.raon.tikitaka.application.territory.in.SelectRepresentativesUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TerritoryScheduler {

    private final EnsureTerritoryCyclesUseCase ensureTerritoryCyclesUseCase;
    private final SelectRepresentativesUseCase selectRepresentativesUseCase;
    private final RunBattleBatchUseCase runBattleBatchUseCase;

    /** 미래 라운드 생성(05:05) 뒤에 돈다. 배포 직후에도 사이클이 있도록 기동 시 1회 실행한다 */
    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 35 5 * * *", zone = "Asia/Seoul")
    public void ensureCycles() {
        try {
            ensureTerritoryCyclesUseCase.execute();
        } catch (Exception e) {
            log.error("땅따먹기 사이클 생성 배치 실패", e);
        }
    }

    /** 포인트 라운드 5일차 새벽. 랭킹 동결(4일차 종료) 이후 아직 선발하지 않은 사이클만 처리한다 */
    @Scheduled(cron = "0 40 5 * * *", zone = "Asia/Seoul")
    public void selectRepresentatives() {
        try {
            selectRepresentativesUseCase.execute();
        } catch (Exception e) {
            log.error("대표 선발 배치 실패", e);
        }
    }

    /** 땅따먹기 1일차 새벽: 미션 없는 땅에 기본 미션. 사이클당 한 번만 처리된다 */
    @Scheduled(cron = "0 45 5 * * *", zone = "Asia/Seoul")
    public void applyDefaultMissions() {
        run("기본 미션 적용", runBattleBatchUseCase::applyDefaultMissions);
    }

    @Scheduled(cron = "0 50 5 * * *", zone = "Asia/Seoul")
    public void recalculateLandPrices() {
        run("땅값 재계산", runBattleBatchUseCase::recalculateLandPrices);
    }

    @Scheduled(cron = "0 55 5 * * *", zone = "Asia/Seoul")
    public void decayDefense() {
        run("방어 점수 감쇠", runBattleBatchUseCase::decayDefense);
    }

    private void run(String name, Runnable job) {
        try {
            job.run();
        } catch (Exception e) {
            log.error("{} 배치 실패", name, e);
        }
    }
}
