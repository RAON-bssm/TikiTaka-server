package com.raon.tikitaka.adapter.scheduler;

import com.raon.tikitaka.application.ranking.in.FinalizeSeasonUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 시즌 확정 배치 — 매일 05:20에 "끝났는데 미확정인 시즌"을 점검한다.
 * 시즌 28일 중 27일은 확정 대상이 없어 조용히 끝나고(no-op),
 * 시즌이 끝난 다음 날 아침 딱 한 번 실제 확정이 일어난다.
 * 05:20인 이유: 05:05(라운드 생성)·05:10(매치 생성)이 끝난 뒤 가장 마지막 순번.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankingScheduler {

    private final FinalizeSeasonUseCase finalizeSeasonUseCase;

    @Scheduled(cron = "0 20 5 * * *", zone = "Asia/Seoul")
    public void finalizeSeason() {
        try {
            finalizeSeasonUseCase.execute();
        } catch (Exception e) {
            log.error("시즌 확정 배치 실패", e);
        }
    }
}
