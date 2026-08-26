package com.raon.tikitaka.adapter.scheduler;

import com.raon.tikitaka.application.ranking.in.FinalizeSeasonUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 시즌 확정 배치. 매일 05:20에 끝났는데 미확정인 시즌을 점검한다.
 * 평소에는 확정 대상이 없어 조용히 끝나고 시즌이 끝난 다음 날 아침 한 번만 실제 확정이 일어난다.
 * 05:20은 라운드 생성과 매치 생성이 끝난 뒤의 순번이다.
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
