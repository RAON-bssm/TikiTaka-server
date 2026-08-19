package com.raon.tikitaka.adapter.match.out;

import com.raon.tikitaka.application.match.out.MissionGeneratorPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 키워드 조합을 Gemini로 자연스러운 미션 문장으로 다듬는다.
 * 실패하면(타임아웃·파싱 오류·이상 응답) 템플릿 폴백을 반환한다 — 절대 예외를 던지지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiMissionAdapter implements MissionGeneratorPort {

    private final ObjectMapper objectMapper;
    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://generativelanguage.googleapis.com")
            .build();

    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${gemini.model}")
    private String model;

    @Override
    public String generate(String adjective, String noun) {
        String fallback = String.format("이번 주 미션: 우리 동네에서 '%s %s'을(를) 찾아 사진에 담아보세요!", adjective, noun);

        try {
            String prompt = String.format(
                    "'%s'와(과) '%s'을(를) 조합해서, 동네 주민들이 일주일 동안 수행할 사진 찍기 미션을 "
                            + "한국어 한 문장으로 만들어줘. 재미있고 친근한 말투로, 설명이나 따옴표 없이 미션 문장만 출력해.",
                    adjective, noun);

            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(
                                    Map.of("text", prompt)
                            ))
                    )
            );

            String responseBody = webClient.post()
                    .uri("/v1beta/models/{model}:generateContent?key={apiKey}", model, apiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(15));

            JsonNode root = objectMapper.readTree(responseBody);
            String mission = root.path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asString().trim();

            if (mission.isBlank() || mission.length() > 200) {
                log.warn("Gemini 미션 응답이 비정상이라 템플릿으로 대체합니다: '{}'", mission);
                return fallback;
            }
            log.info("Gemini 미션 생성 성공: {}", mission);
            return mission;

        } catch (Exception e) {
            log.warn("Gemini 미션 생성 실패 — 템플릿으로 대체합니다 ({} {}): {}", adjective, noun, e.getMessage());
            return fallback;
        }
    }
}
