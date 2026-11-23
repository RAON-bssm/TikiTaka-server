package com.raon.tikitaka.application.push;

import com.raon.tikitaka.global.config.PushProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 알림 메시지를 만든다. 문구는 PushProperties(환경변수 PUSH_*)에서 읽고, data(type 등)는 코드로 고정한다.
 * data의 type으로 앱이 알림 종류를 구분해 이동할 화면을 고르므로 type 값은 앱과 약속이라 설정으로 빼지 않는다.
 */
@Component
@RequiredArgsConstructor
public class PushNotifications {

    public static final String TYPE_KEY = "type";
    public static final String DONG_CODE_KEY = "dong_code";
    public static final String TYPE_DAILY_WAGE = "DAILY_WAGE";
    public static final String TYPE_TERRITORY_CAPTURED = "TERRITORY_CAPTURED";

    /** 도전자 닉네임을 알 수 없을 때(이론상 없음) 쓰는 이름 */
    static final String UNKNOWN_USER = "누군가";

    private final PushProperties properties;

    /** 일급 알림. 그날 일급을 받은 땅 주인에게 보낸다 */
    public PushMessage dailyWage() {
        return new PushMessage(properties.title(), properties.dailyWageBody(), Map.of(TYPE_KEY, TYPE_DAILY_WAGE));
    }

    /**
     * 땅을 뺏겼을 때 이전 주인에게 보내는 알림. 문구의 {challenger}, {dong}을 실제 값으로 바꾼다.
     * 동 이름이 없으면 동 없는 문구(territoryCapturedBodyNoDong)를 쓴다.
     */
    public PushMessage territoryCaptured(String challengerName, String dongName, Long dongCode) {
        String who = hasText(challengerName) ? challengerName : UNKNOWN_USER;
        String body = hasText(dongName)
                ? properties.territoryCapturedBody().replace(PushProperties.CHALLENGER, who).replace(PushProperties.DONG, dongName)
                : properties.territoryCapturedBodyNoDong().replace(PushProperties.CHALLENGER, who);
        Map<String, String> data = new HashMap<>();
        data.put(TYPE_KEY, TYPE_TERRITORY_CAPTURED);
        if (dongCode != null) {
            data.put(DONG_CODE_KEY, dongCode.toString());
        }
        return new PushMessage(properties.title(), body, data);
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
