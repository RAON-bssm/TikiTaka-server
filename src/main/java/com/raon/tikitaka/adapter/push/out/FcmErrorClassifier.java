package com.raon.tikitaka.adapter.push.out;

import com.google.firebase.messaging.MessagingErrorCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * FCM 응답을 토큰별로 "어떻게 처리할지"로 분류한다. Firebase SDK 객체 없이 테스트할 수 있게 따로 뺐다.
 *
 * 원칙: 확실히 죽은 토큰만 지운다. 잘못 지우면 정상 유저가 다음 앱 실행 전까지 알림을 못 받는다.
 * - UNREGISTERED: 앱 삭제·데이터 초기화·토큰 만료 → 삭제
 * - INVALID_ARGUMENT: 토큰 형식 오류일 때만 삭제. 메시지(페이로드) 오류여도 같은 코드가 오므로
 *   에러 메시지에 "registration token"이 있을 때만 토큰 문제로 본다.
 *   또 한 묶음(2개 이상)이 전부 INVALID_ARGUMENT면 메시지 쪽 문제로 보고 아무것도 지우지 않는다(대량 삭제 방지)
 * - SENDER_ID_MISMATCH, THIRD_PARTY_AUTH_ERROR: Firebase 프로젝트 혼용, APNs 키 문제 등 설정 오류 → 유지
 * - 그 외(UNAVAILABLE, INTERNAL, QUOTA_EXCEEDED, 알 수 없음): 일시 오류 → 유지
 */
final class FcmErrorClassifier {

    private FcmErrorClassifier() {
    }

    enum Action {
        SENT,
        DELETE_TOKEN,
        PAYLOAD_ERROR,
        CONFIG_ERROR,
        TRANSIENT_ERROR
    }

    /** 토큰 하나의 응답. 성공이면 code와 message는 null */
    record Response(boolean success, MessagingErrorCode code, String message) {

        static Response ok() {
            return new Response(true, null, null);
        }

        static Response failed(MessagingErrorCode code, String message) {
            return new Response(false, code, message);
        }
    }

    static List<Action> classify(List<Response> responses) {
        boolean allInvalidArgument = responses.size() > 1 && responses.stream()
                .allMatch(r -> !r.success() && r.code() == MessagingErrorCode.INVALID_ARGUMENT);

        List<Action> actions = new ArrayList<>(responses.size());
        for (Response r : responses) {
            actions.add(classify(r, allInvalidArgument));
        }
        return actions;
    }

    private static Action classify(Response r, boolean allInvalidArgument) {
        if (r.success()) {
            return Action.SENT;
        }
        if (r.code() == null) {
            return Action.TRANSIENT_ERROR;
        }
        return switch (r.code()) {
            case UNREGISTERED -> Action.DELETE_TOKEN;
            case INVALID_ARGUMENT -> !allInvalidArgument && mentionsToken(r.message())
                    ? Action.DELETE_TOKEN
                    : Action.PAYLOAD_ERROR;
            case SENDER_ID_MISMATCH, THIRD_PARTY_AUTH_ERROR -> Action.CONFIG_ERROR;
            default -> Action.TRANSIENT_ERROR;
        };
    }

    private static boolean mentionsToken(String message) {
        return message != null && message.toLowerCase(Locale.ROOT).contains("registration token");
    }
}
