package com.raon.tikitaka.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 챗봇 AI 서버 연동 설정. application.yaml의 chatbot 블록과 매핑된다.
 *
 * @param baseUrl        FastAPI AI 서버 주소. 같은 compose 네트워크면 서비스명으로 부른다
 * @param connectTimeout 연결 타임아웃. 노트북이 꺼져 있으면 여기서 빨리 끊긴다
 * @param readTimeout    응답 타임아웃. 로컬 LLM은 느려서 기본값이면 앱이 먼저 끊긴다
 * @param historyTurns   AI 서버에 실어 보낼 지난 대화 턴 수 (한 턴 = 질문 + 답변)
 */
@ConfigurationProperties(prefix = "chatbot")
public record ChatbotProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        int historyTurns
) {
}
