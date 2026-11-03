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
 * 타임아웃이나 응답 이상으로 실패하면 템플릿 문장을 대신 반환하고 예외는 던지지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiMissionAdapter implements MissionGeneratorPort {

    /** 프롬프트로 요청하는 미션 문장의 최대 길이(공백 포함). 검증은 이 값의 두 배까지 여유를 둔다. */
    static final int MAX_MISSION_LENGTH = 25;

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
        String fallback = fallbackMission(adjective, noun);

        try {
            String prompt = String.format(
                    "'%s'와(과) '%s'을(를) 조합해서, 동네 주민들이 일주일 동안 수행할 사진 찍기 미션을 만들어줘. "
                            + "말투는 세상에서 가장 귀여운 돌멩이처럼 작고 앙증맞게 해줘. "
                            + "돌멩이는 말투를 비유한 것일 뿐이니, 키워드가 아니라면 문장에 넣지 마. "
                            + "한국어 한 문장, 공백 포함 %d자 이내로 아주 짧게 써줘. "
                            + "말투 예시: 몽글몽글 작은 동그라미 찾아 찍기! "
                            + "예시는 말투만 참고하고, 예시에 나온 단어는 쓰지 마. 입력된 두 키워드만 사용해. "
                            + "이모지, 설명, 따옴표 없이 미션 문장만 출력해.",
                    adjective, noun, MAX_MISSION_LENGTH);

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

            if (!isValidMission(mission)) {
                log.warn("Gemini 미션 응답이 비정상이라 템플릿으로 대체합니다: '{}'", mission);
                return fallback;
            }
            log.info("Gemini 미션 생성 성공: {}", mission);
            return mission;

        } catch (Exception e) {
            log.warn("Gemini 미션 생성 실패. 템플릿으로 대체합니다 ({} {}): {}", adjective, noun, e.getMessage());
            return fallback;
        }
    }

    /**
     * Gemini 응답을 미션 문장으로 쓸 수 있는지 판단한다.
     * 비어 있거나 {@link #MAX_MISSION_LENGTH}의 두 배를 넘으면 쓸 수 없다고 본다.
     * 모델이 요청 길이를 조금 넘기는 정도는 허용하기 위한 여유다.
     */
    static boolean isValidMission(String mission) {
        return mission != null && !mission.isBlank() && mission.length() <= MAX_MISSION_LENGTH * 2;
    }

    /** Gemini 호출이 실패했을 때 쓰는 짧은 템플릿 미션 문장을 만든다. */
    static String fallbackMission(String adjective, String noun) {
        return String.format("'%s %s' 찾아서 찍기!", adjective, noun);
    }
}
