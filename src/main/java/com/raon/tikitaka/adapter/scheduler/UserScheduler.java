package com.raon.tikitaka.adapter.scheduler;

import com.raon.tikitaka.application.user.in.DeactivateInactiveUsersUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 휴면 전환 배치 — 매일 05:30, lastActiveAt이 7일 지난 ACTIVE 유저를 DORMANT로.
 * 8단계부터 스케줄 등록을 미뤄왔던 배치 — 로그인 브랜치 병합으로
 * 로그인 성공 시 touch()가 호출되면서(AuthService.login) 전제 조건이 충족되어 이제 켠다.
 * 05:30인 이유: 새벽 배치 시간표(00:10 스위칭 → 05:05 라운드 → 05:10 매치 → 05:20 시즌 확정)의 마지막 순번.
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
