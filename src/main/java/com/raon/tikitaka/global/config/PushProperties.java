package com.raon.tikitaka.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 푸시 알림 문구(application.yaml의 push 블록 ← 환경변수 PUSH_*).
 * 환경변수가 비어 있으면 아래 기본 문구를 쓴다. 문구를 바꾸면 컨테이너를 다시 만들어야 반영된다(docker compose up -d).
 *
 * 땅 뺏김 문구의 자리표시자: {challenger} = 도전자 닉네임, {dong} = 동 이름.
 * 자리표시자를 빼먹거나 오타를 내면 알림에 그대로 노출되므로, 형식이 틀리면 기동을 실패시킨다.
 *
 * @param title                       모든 알림의 제목
 * @param dailyWageBody               일급 알림 본문
 * @param territoryCapturedBody       땅 뺏김 알림 본문. {challenger}, {dong} 필수
 * @param territoryCapturedBodyNoDong 동 이름을 모를 때의 땅 뺏김 본문. {challenger} 필수, {dong} 사용 불가
 */
@ConfigurationProperties(prefix = "push")
public record PushProperties(
        String title,
        String dailyWageBody,
        String territoryCapturedBody,
        String territoryCapturedBodyNoDong) {

    public static final String CHALLENGER = "{challenger}";
    public static final String DONG = "{dong}";

    static final String DEFAULT_TITLE = "티키타카";
    static final String DEFAULT_DAILY_WAGE_BODY = "출석 보상이 기다리는 중입니다!!";
    static final String DEFAULT_TERRITORY_CAPTURED_BODY = CHALLENGER + "에게 " + DONG + " 땅을 뺏겼습니다!!";
    static final String DEFAULT_TERRITORY_CAPTURED_BODY_NO_DONG = CHALLENGER + "에게 땅을 뺏겼습니다!!";

    public PushProperties {
        title = orDefault(title, DEFAULT_TITLE);
        dailyWageBody = orDefault(dailyWageBody, DEFAULT_DAILY_WAGE_BODY);
        territoryCapturedBody = orDefault(territoryCapturedBody, DEFAULT_TERRITORY_CAPTURED_BODY);
        territoryCapturedBodyNoDong = orDefault(territoryCapturedBodyNoDong, DEFAULT_TERRITORY_CAPTURED_BODY_NO_DONG);

        if (!territoryCapturedBody.contains(CHALLENGER) || !territoryCapturedBody.contains(DONG)) {
            throw new IllegalArgumentException("PUSH_TERRITORY_CAPTURED_BODY에는 " + CHALLENGER + "와 " + DONG
                    + "가 모두 있어야 합니다. 현재 값: " + territoryCapturedBody);
        }
        if (!territoryCapturedBodyNoDong.contains(CHALLENGER) || territoryCapturedBodyNoDong.contains(DONG)) {
            throw new IllegalArgumentException("PUSH_TERRITORY_CAPTURED_BODY_NO_DONG에는 " + CHALLENGER + "가 있어야 하고 "
                    + DONG + "는 쓸 수 없습니다. 현재 값: " + territoryCapturedBodyNoDong);
        }
    }

    /** 모든 값이 기본 문구인 설정. 테스트에서 쓴다 */
    public static PushProperties defaults() {
        return new PushProperties(null, null, null, null);
    }

    private static String orDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
