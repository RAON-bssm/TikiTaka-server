package com.raon.tikitaka.application.push;

import com.raon.tikitaka.application.push.out.PushSenderPort;
import com.raon.tikitaka.application.push.out.PushTokenPort;
import com.raon.tikitaka.domain.enums.AppPlatform;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PushServiceTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final PushMessage MESSAGE = new PushMessage("제목", "본문", Map.of("type", "TEST"));

    private final PushTokenPort tokenPort = mock(PushTokenPort.class);
    private final PushSenderPort senderPort = mock(PushSenderPort.class);
    private final PushService service = new PushService(tokenPort, senderPort);

    // ---- 등록·해제 ----

    @Test
    void 토큰을_등록하면_앞뒤_공백을_지우고_플랫폼을_해석해_upsert한다() {
        service.register(A, "  token-1  ", "IOS");

        verify(tokenPort).upsert(eq(A), eq("token-1"), eq(AppPlatform.IOS), any(LocalDateTime.class));
    }

    @Test
    void 빈_토큰은_400() {
        assertThatThrownBy(() -> service.register(A, " ", "android"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(PushService.EMPTY_TOKEN_MESSAGE);
        verifyNoInteractions(tokenPort);
    }

    @Test
    void 너무_긴_토큰은_400() {
        String tooLong = "a".repeat(PushService.MAX_TOKEN_LENGTH + 1);

        assertThatThrownBy(() -> service.register(A, tooLong, "android"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(PushService.TOO_LONG_TOKEN_MESSAGE);
    }

    @Test
    void 모르는_플랫폼은_400() {
        assertThatThrownBy(() -> service.register(A, "token-1", "windows"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(PushService.INVALID_PLATFORM_MESSAGE);
        verifyNoInteractions(tokenPort);
    }

    @Test
    void 해제는_내_토큰만_지운다() {
        service.unregister(A, "token-1");

        verify(tokenPort).delete(A, "token-1");
    }

    // ---- 발송 ----

    @Test
    void 유저의_모든_기기로_보내고_무효_토큰만_지운다() {
        when(tokenPort.findTokensByUserIds(List.of(A))).thenReturn(List.of("phone", "tablet"));
        when(senderPort.send(List.of("phone", "tablet"), MESSAGE))
                .thenReturn(new PushSendResult(1, List.of("tablet")));

        service.sendToUser(A, MESSAGE);

        verify(senderPort).send(List.of("phone", "tablet"), MESSAGE);
        verify(tokenPort).deleteAllByTokens(List.of("tablet"));
    }

    @Test
    void 무효_토큰이_없으면_아무것도_지우지_않는다() {
        when(tokenPort.findTokensByUserIds(any())).thenReturn(List.of("phone"));
        when(senderPort.send(any(), any())).thenReturn(new PushSendResult(1, List.of()));

        service.sendToUser(A, MESSAGE);

        verify(tokenPort, never()).deleteAllByTokens(anyCollection());
    }

    @Test
    void 토큰이_없는_유저면_FCM을_부르지_않는다() {
        when(tokenPort.findTokensByUserIds(any())).thenReturn(List.of());

        service.sendToUser(A, MESSAGE);

        verifyNoInteractions(senderPort);
    }

    @Test
    void 대상이_없으면_DB도_FCM도_부르지_않는다() {
        service.sendToUsers(List.of(), MESSAGE);

        verifyNoInteractions(tokenPort, senderPort);
    }

    @Test
    void 같은_유저가_여러_번_들어와도_한_번만_조회한다() {
        when(tokenPort.findTokensByUserIds(any())).thenReturn(List.of());

        service.sendToUsers(List.of(A, B, A), MESSAGE);

        verify(tokenPort).findTokensByUserIds(List.of(A, B));
    }

    @Test
    @SuppressWarnings("unchecked")
    void 유저가_많으면_1000명씩_나눠_조회한다() {
        List<UUID> many = new ArrayList<>();
        for (int i = 0; i < PushService.USER_ID_CHUNK + 1; i++) {
            many.add(UUID.randomUUID());
        }
        when(tokenPort.findTokensByUserIds(any())).thenReturn(List.of());

        service.sendToUsers(many, MESSAGE);

        ArgumentCaptor<Collection<UUID>> chunks = ArgumentCaptor.forClass(Collection.class);
        verify(tokenPort, times(2)).findTokensByUserIds(chunks.capture());
        assertThat(chunks.getAllValues().get(0)).hasSize(PushService.USER_ID_CHUNK);
        assertThat(chunks.getAllValues().get(1)).hasSize(1);
    }

    // ---- 정리 ----

    @Test
    void 정리는_60일_넘게_갱신되지_않은_토큰을_지운다() {
        LocalDateTime before = LocalDateTime.now().minusDays(PushService.STALE_DAYS);

        service.cleanUpStaleTokens();

        ArgumentCaptor<LocalDateTime> threshold = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(tokenPort).deleteNotUpdatedSince(threshold.capture());
        assertThat(threshold.getValue()).isBetween(before, LocalDateTime.now().minusDays(PushService.STALE_DAYS));
    }
}
