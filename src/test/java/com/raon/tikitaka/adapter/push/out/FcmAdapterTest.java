package com.raon.tikitaka.adapter.push.out;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import com.raon.tikitaka.application.push.PushMessage;
import com.raon.tikitaka.application.push.PushSendResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FcmAdapterTest {

    private static final PushMessage MESSAGE = new PushMessage("제목", "본문", Map.of("type", "TEST"));

    @Test
    void 키가_없으면_기동은_되고_발송만_건너뛴다() {
        FcmAdapter adapter = new FcmAdapter("");

        PushSendResult result = adapter.send(List.of("t1"), MESSAGE);

        assertThat(result.successCount()).isZero();
        assertThat(result.invalidTokens()).isEmpty();
    }

    @Test
    void 키가_있는데_읽을_수_없으면_기동을_실패시킨다() {
        assertThatThrownBy(() -> new FcmAdapter("not base64!!"))
                .isInstanceOf(IllegalStateException.class);
        String notJson = Base64.getEncoder().encodeToString("hello".getBytes());
        assertThatThrownBy(() -> new FcmAdapter(notJson))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 무효_토큰만_골라서_돌려준다() throws Exception {
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        BatchResponse batch = mock(BatchResponse.class);
        when(batch.getSuccessCount()).thenReturn(1);
        when(batch.getResponses()).thenReturn(List.of(ok(), failed(MessagingErrorCode.UNREGISTERED),
                failed(MessagingErrorCode.UNAVAILABLE)));
        when(messaging.sendEachForMulticast(any(MulticastMessage.class))).thenReturn(batch);

        PushSendResult result = new FcmAdapter(messaging)
                .send(List.of("alive", "uninstalled", "busy"), MESSAGE);

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.invalidTokens()).containsExactly("uninstalled");
    }

    @Test
    void 요청_자체가_실패하면_아무_토큰도_지우지_않는다() throws Exception {
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        when(messaging.sendEachForMulticast(any(MulticastMessage.class)))
                .thenThrow(mock(FirebaseMessagingException.class));

        PushSendResult result = new FcmAdapter(messaging).send(List.of("t1"), MESSAGE);

        assertThat(result.invalidTokens()).isEmpty();
    }

    @Test
    void 토큰이_500개를_넘으면_나눠서_보낸다() throws Exception {
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        BatchResponse batch = mock(BatchResponse.class);
        when(batch.getResponses()).thenReturn(List.of());
        when(messaging.sendEachForMulticast(any(MulticastMessage.class))).thenReturn(batch);
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < FcmAdapter.MULTICAST_LIMIT + 1; i++) {
            tokens.add("t" + i);
        }

        new FcmAdapter(messaging).send(tokens, MESSAGE);

        verify(messaging, times(2)).sendEachForMulticast(any(MulticastMessage.class));
    }

    private static SendResponse ok() {
        SendResponse r = mock(SendResponse.class);
        when(r.isSuccessful()).thenReturn(true);
        return r;
    }

    private static SendResponse failed(MessagingErrorCode code) {
        FirebaseMessagingException e = mock(FirebaseMessagingException.class);
        when(e.getMessagingErrorCode()).thenReturn(code);
        when(e.getMessage()).thenReturn(code.name());
        SendResponse r = mock(SendResponse.class);
        when(r.isSuccessful()).thenReturn(false);
        when(r.getException()).thenReturn(e);
        return r;
    }
}
