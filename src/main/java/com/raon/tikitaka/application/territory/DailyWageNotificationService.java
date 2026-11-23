package com.raon.tikitaka.application.territory;

import com.raon.tikitaka.application.push.PushNotifications;
import com.raon.tikitaka.application.push.in.SendPushUseCase;
import com.raon.tikitaka.application.territory.in.NotifyDailyWageUseCase;
import com.raon.tikitaka.application.territory.out.TerritoryWagePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 일급 알림. territory_wage에서 오늘(paid_on) 지급 기록이 있는 유저를 찾아 "출석 보상이 기다리는 중입니다!!"를 보낸다.
 * 지급 기록은 일급이 0보다 클 때만 남으므로 0원인 땅 주인에게는 가지 않는다.
 *
 * 지급(DailyWageService)과 분리한 이유: 지급은 05:57 새벽이라 바로 보내면 새벽에 폰이 울린다.
 * 트랜잭션을 걸지 않는다. 조회는 짧고, 발송은 FCM 호출이라 DB 커넥션을 붙잡지 않게 한다.
 * 같은 날 두 번 돌리면 두 번 보낸다(스케줄러가 하루 한 번만 부른다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyWageNotificationService implements NotifyDailyWageUseCase {

    private final TerritoryWagePort wagePort;
    private final SendPushUseCase sendPushUseCase;
    private final PushNotifications pushNotifications;

    @Override
    public void notifyTodayWage() {
        List<UUID> userIds = wagePort.findUserIdsPaidOn(LocalDate.now());
        if (userIds.isEmpty()) {
            log.info("오늘 일급을 받은 유저가 없어 일급 알림을 보내지 않습니다.");
            return;
        }
        sendPushUseCase.sendToUsers(userIds, pushNotifications.dailyWage());
        log.info("일급 알림 발송 완료. 대상 {}명", userIds.size());
    }
}
