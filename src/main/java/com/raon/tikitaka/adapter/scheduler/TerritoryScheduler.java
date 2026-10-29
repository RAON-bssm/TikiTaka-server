package com.raon.tikitaka.adapter.scheduler;

import com.raon.tikitaka.application.territory.in.EnsureTerritoryCyclesUseCase;
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
}
