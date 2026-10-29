package com.raon.tikitaka.adapter.review.out;

import com.raon.tikitaka.application.review.AiReviewInvalidResponseException;
import com.raon.tikitaka.application.review.AiReviewRejectedException;
import com.raon.tikitaka.application.review.AiReviewResult;
import com.raon.tikitaka.application.review.AiReviewRetryableException;
import com.raon.tikitaka.application.review.out.AiReviewPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiAdapter implements AiReviewPort {

    /** 응답이 오지 않으면 스레드가 영구히 묶이므로 응답 대기 시간에 상한을 둔다 */
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(30);

    /** block()의 상한. 응답 타임아웃이 먼저 걸리도록 연결·전송 시간만큼 여유를 둔다 */
    private static final Duration BLOCK_TIMEOUT = RESPONSE_TIMEOUT.plusSeconds(5);

    private final ObjectMapper objectMapper;
    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://generativelanguage.googleapis.com")
            .clientConnector(new ReactorClientHttpConnector(HttpClient.create().responseTimeout(RESPONSE_TIMEOUT)))
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
                ),
                // JSON 모드 강제. 없으면 모델이 가끔 코드펜스 섞인 응답을 보내 parse()가 깨진다
                "generationConfig", Map.of(
                        "response_mime_type", "application/json"
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
                    .block(BLOCK_TIMEOUT);
        } catch (WebClientResponseException e) {
            log.error("Gemini API 호출 실패: model={}, status={}, elapsedMs={}, retryAfter={}, rateLimitHeaders={}, body={}",
                    model,
                    e.getStatusCode(),
                    System.currentTimeMillis() - startedAt,
                    e.getHeaders().getFirst("Retry-After"),
                    extractRateLimitHeaders(e),
                    e.getResponseBodyAsString());
            throw classify(e);
        } catch (WebClientRequestException e) {
            // 연결 실패와 응답 타임아웃(ReadTimeoutException)이 여기로 온다
            log.error("Gemini API 호출 실패(네트워크·타임아웃): model={}, elapsedMs={}, cause={}",
                    model, System.currentTimeMillis() - startedAt, e.getMessage());
            throw new AiReviewRetryableException("Gemini 호출 네트워크 오류", null, e);
        } catch (IllegalStateException e) {
            // block(Duration)이 시간 안에 끝나지 않으면 IllegalStateException을 던진다
            log.error("Gemini API 호출 실패(대기 시간 초과): model={}, elapsedMs={}, cause={}",
                    model, System.currentTimeMillis() - startedAt, e.getMessage());
            throw new AiReviewRetryableException("Gemini 호출 대기 시간 초과", null, e);
        }

        log.info("Gemini API 호출 성공: model={}, elapsedMs={}", model, System.currentTimeMillis() - startedAt);
        return parse(responseBody);
    }

    /**
     * 429와 5xx는 다시 시도할 수 있고, 그 밖의 4xx(콘텐츠 정책 차단 등)는 다시 보내도 같다.
     * 다시 시도할 시점은 Retry-After 헤더를 먼저 보고, 없으면 Gemini 오류 본문의 RetryInfo.retryDelay를 본다.
     */
    RuntimeException classify(WebClientResponseException e) {
        int status = e.getStatusCode().value();
        if (status == 429 || e.getStatusCode().is5xxServerError()) {
            return new AiReviewRetryableException("Gemini 일시 오류 status=" + status, retryAfter(e), e);
        }
        return new AiReviewRejectedException("Gemini 요청 거절 status=" + status, status, e);
    }

    private Duration retryAfter(WebClientResponseException e) {
        Duration fromHeader = parseRetryAfterHeader(e.getHeaders().getFirst("Retry-After"));
        if (fromHeader != null) {
            return fromHeader;
        }
        return parseRetryDelay(e.getResponseBodyAsString());
    }

    /** Retry-After는 초 단위 숫자이거나 HTTP 날짜다. 읽을 수 없으면 null을 돌려 기본 백오프를 쓰게 한다 */
    static Duration parseRetryAfterHeader(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            long seconds = Long.parseLong(trimmed);
            return seconds >= 0 ? Duration.ofSeconds(seconds) : null;
        } catch (NumberFormatException ignored) {
            // 숫자가 아니면 HTTP 날짜로 읽어본다
        }
        try {
            ZonedDateTime at = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME);
            Duration until = Duration.between(ZonedDateTime.now(at.getZone()), at);
            return until.isNegative() ? Duration.ZERO : until;
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    /**
     * Gemini는 429 본문의 error.details에 {"@type": "...RetryInfo", "retryDelay": "17s"} 형태로 대기 시간을 준다.
     * 형식이 다르거나 없으면 null이다.
     */
    Duration parseRetryDelay(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            for (JsonNode detail : objectMapper.readTree(body).path("error").path("details")) {
                String delay = detail.path("retryDelay").asString("");
                if (delay.endsWith("s")) {
                    double seconds = Double.parseDouble(delay.substring(0, delay.length() - 1));
                    return Duration.ofMillis(Math.round(seconds * 1000));
                }
            }
        } catch (JacksonException | NumberFormatException ignored) {
            // 본문을 읽지 못하면 기본 백오프를 쓴다
        }
        return null;
    }

    private Map<String, String> extractRateLimitHeaders(WebClientResponseException e) {
        Map<String, String> headers = new LinkedHashMap<>();
        e.getHeaders().forEach((name, values) -> {
            if (name.toLowerCase().contains("ratelimit") || name.toLowerCase().contains("quota")) {
                headers.put(name, String.join(",", values));
            }
        });
        return headers;
    }

    /**
     * 응답 구조가 기대와 다르거나 JSON이 아니면 응답 형식 오류(재시도 대상)로 던진다.
     * 안전 필터로 막히면 candidates 없이 promptFeedback만 오는데, 이것도 여기서 형식 오류가 된다.
     */
    AiReviewResult parse(String responseBody) {
        String text;
        try {
            JsonNode part = objectMapper.readTree(responseBody).path("candidates").path(0)
                    .path("content").path("parts").path(0);
            if (!part.hasNonNull("text")) {
                log.error("Gemini 응답에 심사 텍스트가 없습니다. 원문: {}", responseBody);
                throw new AiReviewInvalidResponseException("Gemini 응답에 심사 텍스트가 없습니다");
            }
            text = part.path("text").asString();
        } catch (JacksonException e) {
            log.error("Gemini 응답 본문을 읽을 수 없습니다. 원문: {}", responseBody);
            throw new AiReviewInvalidResponseException("Gemini 응답 본문을 읽을 수 없습니다", e);
        }

        JsonNode result;
        try {
            result = objectMapper.readTree(text);
        } catch (JacksonException e) {
            log.error("Gemini 응답이 JSON 형식이 아닙니다. 원문: {}", text);
            throw new AiReviewInvalidResponseException("Gemini 응답이 JSON 형식이 아닙니다", e);
        }

        // score가 누락되면 asInt()가 0을 반환해 0점 게시물이 저장될 수 있다
        // 누락 시 null을 돌려주고 ReviewService의 범위 검증이 재시도 대상 예외로 거른다
        Integer score = null;
        if (result.hasNonNull("score")) {
            score = result.path("score").asInt();
        }
        return new AiReviewResult(score, result.path("review").asString());
    }
}
