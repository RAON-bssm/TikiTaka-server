package com.raon.tikitaka.adapter.scheduler;

import com.raon.tikitaka.application.user.in.DeactivateInactiveUsersUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 휴면 전환 배치. 매일 05:30에 lastActiveAt이 7일 지난 ACTIVE 유저를 DORMANT로 바꾼다.
 * 로그인 성공 시 touch()가 호출되는 것이 전제 조건이다.
 * 05:30은 새벽 배치 시간표의 마지막 순번이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserScheduler {

    private final DeactivateInactiveUsersUseCase deactivateInactiveUsersUseCase;

    @Scheduled(cron = "0 30 5 * * *", zone = "Asia/Seoul")
    public void deactivateInactiveUsers() {
        try {
            deactivateInactiveUsersUseCase.execute();
        } catch (Exception e) {
            log.error("휴면 전환 배치 실패", e);
        }
    }
}
