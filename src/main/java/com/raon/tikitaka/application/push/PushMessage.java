package com.raon.tikitaka.application.push;

import java.util.Map;

/**
 * 보낼 알림 한 건. Firebase 타입을 서비스에 드러내지 않으려고 따로 둔다.
 * data는 앱이 알림을 눌렀을 때 이동할 화면을 고르는 데 쓴다(FCM 규칙상 값은 전부 문자열).
 */
public record PushMessage(String title, String body, Map<String, String> data) {

    public PushMessage {
        data = data == null ? Map.of() : Map.copyOf(data);
    }
}
