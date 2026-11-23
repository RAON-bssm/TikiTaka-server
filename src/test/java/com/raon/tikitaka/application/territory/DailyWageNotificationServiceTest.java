package com.raon.tikitaka.application.territory;

import com.raon.tikitaka.application.push.PushNotifications;
import com.raon.tikitaka.application.push.in.SendPushUseCase;
import com.raon.tikitaka.application.territory.out.TerritoryWagePort;
import com.raon.tikitaka.global.config.PushProperties;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DailyWageNotificationServiceTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private final TerritoryWagePort wagePort = mock(TerritoryWagePort.class);
    private final SendPushUseCase sendPushUseCase = mock(SendPushUseCase.class);
    private final PushNotifications notifications = new PushNotifications(PushProperties.defaults());
    private final DailyWageNotificationService service =
            new DailyWageNotificationService(wagePort, sendPushUseCase, notifications);

    @Test
    void 오늘_일급을_받은_유저에게_일급_알림을_보낸다() {
        when(wagePort.findUserIdsPaidOn(LocalDate.now())).thenReturn(List.of(A, B));

        service.notifyTodayWage();

        verify(sendPushUseCase).sendToUsers(List.of(A, B), notifications.dailyWage());
    }

    @Test
    void 오늘_받은_유저가_없으면_보내지_않는다() {
        when(wagePort.findUserIdsPaidOn(LocalDate.now())).thenReturn(List.of());

        service.notifyTodayWage();

        verifyNoInteractions(sendPushUseCase);
    }
}
