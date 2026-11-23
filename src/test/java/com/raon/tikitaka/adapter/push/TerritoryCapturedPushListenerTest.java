package com.raon.tikitaka.adapter.push;

import com.raon.tikitaka.application.push.PushNotifications;
import com.raon.tikitaka.application.push.in.SendPushUseCase;
import com.raon.tikitaka.application.territory.TerritoryCapturedEvent;
import com.raon.tikitaka.global.config.PushProperties;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TerritoryCapturedPushListenerTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    private final SendPushUseCase sendPushUseCase = mock(SendPushUseCase.class);
    private final PushNotifications notifications = new PushNotifications(PushProperties.defaults());
    private final TerritoryCapturedPushListener listener = new TerritoryCapturedPushListener(sendPushUseCase, notifications);

    @Test
    void 이전_주인에게_도전자와_동_이름이_든_알림을_보낸다() {
        listener.onCaptured(new TerritoryCapturedEvent(OWNER, "티키", 1L, "온천동"));

        verify(sendPushUseCase).sendToUser(OWNER, notifications.territoryCaptured("티키", "온천동", 1L));
    }

    @Test
    void 발송이_실패해도_예외를_밖으로_던지지_않는다() {
        doThrow(new RuntimeException("FCM down")).when(sendPushUseCase).sendToUser(any(), any());

        assertThatCode(() -> listener.onCaptured(new TerritoryCapturedEvent(OWNER, "티키", 1L, "온천동")))
                .doesNotThrowAnyException();
    }
}
