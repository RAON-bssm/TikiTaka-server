package com.raon.tikitaka.adapter.scheduler;

import com.raon.tikitaka.application.push.in.CleanUpPushTokenUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PushScheduler {

    private final CleanUpPushTokenUseCase cleanUpPushTokenUseCase;

    /** 매일 04:30: 오래 등록되지 않은 토큰 정리. 다른 새벽 배치(05:05~06:00)와 겹치지 않게 앞에 둔다 */
    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    public void cleanUpStaleTokens() {
        try {
            cleanUpPushTokenUseCase.cleanUpStaleTokens();
        } catch (Exception e) {
            log.error("오래된 푸시 토큰 정리 배치 실패", e);
        }
    }
}
