package com.raon.tikitaka.adapter.review.out;

import com.raon.tikitaka.application.review.AiReviewResult;
import com.raon.tikitaka.application.review.out.AiReviewPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiAdapter implements AiReviewPort {

    private final ObjectMapper objectMapper;
    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://generativelanguage.googleapis.com")
            .build();

    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${gemini.model}")
    private String model;

    @Value("${gemini.prompt}")
    private String prompt;

    @Override
    public AiReviewResult review(String mission, String content, byte[] image, String contentType) {
        String text = prompt
                + "\n\n[미션]\n" + mission
                + "\n\n[게시물 내용]\n" + content;

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", text),
                                Map.of("inline_data", Map.of(
                                        "mime_type", contentType,
                                        "data", Base64.getEncoder().encodeToString(image)
                                ))
                        ))
                )
        );

        log.info("Gemini API 호출 시작: model={}, promptChars={}, imageBytes={}, contentType={}",
                model, text.length(), image.length, contentType);

        long startedAt = System.currentTimeMillis();
        String responseBody;
        try {
            responseBody = webClient.post()
                    .uri("/v1beta/models/{model}:generateContent?key={apiKey}", model, apiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("Gemini API 호출 실패: model={}, status={}, elapsedMs={}, retryAfter={}, rateLimitHeaders={}, body={}",
                    model,
                    e.getStatusCode(),
                    System.currentTimeMillis() - startedAt,
                    e.getHeaders().getFirst("Retry-After"),
                    extractRateLimitHeaders(e),
                    e.getResponseBodyAsString());
            throw e;
        }

        log.info("Gemini API 호출 성공: model={}, elapsedMs={}", model, System.currentTimeMillis() - startedAt);
        return parse(responseBody);
    }

    private Map<String, String> extractRateLimitHeaders(WebClientResponseException e) {
        Map<String, String> headers = new java.util.LinkedHashMap<>();
        e.getHeaders().forEach((name, values) -> {
            if (name.toLowerCase().contains("ratelimit") || name.toLowerCase().contains("quota")) {
                headers.put(name, String.join(",", values));
            }
        });
        return headers;
    }

    private AiReviewResult parse(String responseBody) {
        JsonNode root = objectMapper.readTree(responseBody);
        String text = root.path("candidates").get(0)
                .path("content").path("parts").get(0)
                .path("text").asString();

        JsonNode result = objectMapper.readTree(text);
        return new AiReviewResult(result.path("score").asInt(), result.path("review").asString());
    }
}
