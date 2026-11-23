package com.raon.tikitaka.adapter.push.out;

import com.raon.tikitaka.adapter.push.out.FcmErrorClassifier.Action;
import com.raon.tikitaka.adapter.push.out.FcmErrorClassifier.Response;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.google.firebase.messaging.MessagingErrorCode.INTERNAL;
import static com.google.firebase.messaging.MessagingErrorCode.INVALID_ARGUMENT;
import static com.google.firebase.messaging.MessagingErrorCode.QUOTA_EXCEEDED;
import static com.google.firebase.messaging.MessagingErrorCode.SENDER_ID_MISMATCH;
import static com.google.firebase.messaging.MessagingErrorCode.THIRD_PARTY_AUTH_ERROR;
import static com.google.firebase.messaging.MessagingErrorCode.UNAVAILABLE;
import static com.google.firebase.messaging.MessagingErrorCode.UNREGISTERED;
import static org.assertj.core.api.Assertions.assertThat;

class FcmErrorClassifierTest {

    private static final String BAD_TOKEN = "The registration token is not a valid FCM registration token";
    private static final String BAD_PAYLOAD = "Message.data must not contain reserved keywords";

    @Test
    void 성공은_SENT_UNREGISTERED는_삭제() {
        List<Action> actions = FcmErrorClassifier.classify(List.of(
                Response.ok(),
                Response.failed(UNREGISTERED, "Requested entity was not found.")));

        assertThat(actions).containsExactly(Action.SENT, Action.DELETE_TOKEN);
    }

    @Test
    void INVALID_ARGUMENT는_토큰_형식_오류일_때만_삭제한다() {
        List<Action> actions = FcmErrorClassifier.classify(List.of(
                Response.ok(),
                Response.failed(INVALID_ARGUMENT, BAD_TOKEN),
                Response.failed(INVALID_ARGUMENT, BAD_PAYLOAD)));

        assertThat(actions).containsExactly(Action.SENT, Action.DELETE_TOKEN, Action.PAYLOAD_ERROR);
    }

    @Test
    void 묶음_전체가_INVALID_ARGUMENT면_메시지_문제로_보고_하나도_지우지_않는다() {
        List<Action> actions = FcmErrorClassifier.classify(List.of(
                Response.failed(INVALID_ARGUMENT, BAD_TOKEN),
                Response.failed(INVALID_ARGUMENT, BAD_TOKEN)));

        assertThat(actions).containsOnly(Action.PAYLOAD_ERROR);
    }

    @Test
    void 토큰이_하나뿐이면_INVALID_ARGUMENT도_메시지로_판단한다() {
        assertThat(FcmErrorClassifier.classify(List.of(Response.failed(INVALID_ARGUMENT, BAD_TOKEN))))
                .containsExactly(Action.DELETE_TOKEN);
        assertThat(FcmErrorClassifier.classify(List.of(Response.failed(INVALID_ARGUMENT, BAD_PAYLOAD))))
                .containsExactly(Action.PAYLOAD_ERROR);
    }

    @Test
    void 설정_오류는_토큰을_지우지_않는다() {
        List<Action> actions = FcmErrorClassifier.classify(List.of(
                Response.failed(SENDER_ID_MISMATCH, "x"),
                Response.failed(THIRD_PARTY_AUTH_ERROR, "x")));

        assertThat(actions).containsOnly(Action.CONFIG_ERROR);
    }

    @Test
    void 일시_오류와_원인_모를_실패는_토큰을_지우지_않는다() {
        List<Action> actions = FcmErrorClassifier.classify(List.of(
                Response.failed(UNAVAILABLE, "x"),
                Response.failed(INTERNAL, "x"),
                Response.failed(QUOTA_EXCEEDED, "x"),
                Response.failed(null, null)));

        assertThat(actions).containsOnly(Action.TRANSIENT_ERROR);
    }
}
