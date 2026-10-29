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
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiAdapter implements AiReviewPort {

    /** 응답이 오지 않으면 스레드가 영구히 묶이므로 응답 대기 시간에 상한을 둔다 */
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Gemini가 알려준 재시도 대기 시간의 상한. 이보다 길게 미루면 그 글은 사실상 멈춘 것과 같고,
     * 터무니없는 값(1e309초 등)이 다음 시도 시각 계산을 넘치게 하지 않게 한다
     */
    static final Duration MAX_RETRY_AFTER = Duration.ofHours(1);

    /** 요청 자체는 정상일 수 있는 4xx. 키 만료·권한 설정(401, 403)과 요청 시간 초과(408)는 글 내용 탓이 아니다 */
    private static final Set<Integer> RETRYABLE_CLIENT_ERRORS = Set.of(401, 403, 408);

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
     * 429, 5xx와 401, 403, 408은 다시 시도한다. 401, 403은 키·설정 문제라 고치기 전까지 계속 실패하지만,
     * 거절(REJECTED)로 저장하면 관리자 재심사(FAILED만 받음)로 되살릴 수 없어 시도 한도까지 가서 FAILED로 끝나게 둔다.
     * 그 밖의 4xx(400, 404 등)는 다시 보내도 같으므로 거절한다. 상태 코드는 호출부의 오류 로그에 남는다.
     * 다시 시도할 시점은 Retry-After 헤더를 먼저 보고, 없으면 Gemini 오류 본문의 RetryInfo.retryDelay를 본다.
     */
    RuntimeException classify(WebClientResponseException e) {
        int status = e.getStatusCode().value();
        if (status == 429 || e.getStatusCode().is5xxServerError() || RETRYABLE_CLIENT_ERRORS.contains(status)) {
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

    /**
     * Retry-After는 초 단위 숫자이거나 HTTP 날짜다. 읽을 수 없거나 음수, 무한대면 null을 돌려 기본 백오프를 쓰게 하고,
     * MAX_RETRY_AFTER보다 길면 상한으로 줄인다.
     */
    static Duration parseRetryAfterHeader(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return boundedDelay(Double.parseDouble(trimmed));
        } catch (NumberFormatException ignored) {
            // 숫자가 아니면 HTTP 날짜로 읽어본다
        }
        try {
            ZonedDateTime at = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME);
            Duration until = Duration.between(ZonedDateTime.now(at.getZone()), at);
            if (until.isNegative()) {
                return Duration.ZERO;
            }
            return until.compareTo(MAX_RETRY_AFTER) > 0 ? MAX_RETRY_AFTER : until;
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    /**
     * 초 단위 대기 시간을 Duration으로 바꾼다. NaN, 무한대, 음수는 null이고 상한을 넘으면 상한이다.
     * Math.round에 큰 값이 들어가 넘치지 않도록 상한과 먼저 비교한다.
     */
    static Duration boundedDelay(double seconds) {
        if (Double.isNaN(seconds) || Double.isInfinite(seconds) || seconds < 0) {
            return null;
        }
        if (seconds >= MAX_RETRY_AFTER.toSeconds()) {
            return MAX_RETRY_AFTER;
        }
        return Duration.ofMillis(Math.round(seconds * 1000));
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
                    return boundedDelay(Double.parseDouble(delay.substring(0, delay.length() - 1)));
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
     * 안전 필터에 막힌 응답은 200으로 오지만 다시 보내도 같으므로 거절 대상으로 던진다. 판정 조건은
     * promptFeedback.blockReason이 안전 사유이거나, 후보에 쓸 텍스트가 없고 finishReason이 안전 사유인 경우다.
     * 둘 다 아닌 빈 응답(후보 없음, MAX_TOKENS 등)은 일시적일 수 있어 지금처럼 형식 오류로 둔다.
     */
    AiReviewResult parse(String responseBody) {
        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (JacksonException e) {
            log.error("Gemini 응답 본문을 읽을 수 없습니다. 원문: {}", responseBody);
            throw new AiReviewInvalidResponseException("Gemini 응답 본문을 읽을 수 없습니다", e);
        }

        JsonNode promptFeedback = root.path("promptFeedback");
        JsonNode candidate = root.path("candidates").path(0);
        String blockReason = textOrNull(promptFeedback.path("blockReason"));
        String finishReason = textOrNull(candidate.path("finishReason"));
        String text = textOrNull(candidate.path("content").path("parts").path(0).path("text"));

        boolean blockedPrompt = blockReason != null && GeminiSafetyBlock.BLOCK_REASONS.contains(blockReason);
        boolean blockedCandidate = text == null && finishReason != null
                && GeminiSafetyBlock.FINISH_REASONS.contains(finishReason);
        if (blockedPrompt || blockedCandidate) {
            // 이미지·게시물 내용·응답 원문은 남기지 않고 차단 사유만 남긴다
            log.warn("Gemini 안전 필터 차단: model={}, blockReason={}, finishReason={}, categories={}",
                    model, blockReason, finishReason, flaggedCategories(promptFeedback, candidate));
            throw new AiReviewRejectedException("Gemini 안전 필터 차단 blockReason=" + blockReason
                    + ", finishReason=" + finishReason, 200, null);
        }
        if (text == null) {
            log.error("Gemini 응답에 심사 텍스트가 없습니다. 원문: {}", responseBody);
            throw new AiReviewInvalidResponseException("Gemini 응답에 심사 텍스트가 없습니다");
        }

        JsonNode result;
        try {
            result = objectMapper.readTree(text);
        } catch (JacksonException e) {
            log.error("Gemini 응답이 JSON 형식이 아닙니다. 원문: {}", text);
            throw new AiReviewInvalidResponseException("Gemini 응답이 JSON 형식이 아닙니다", e);
        }

        return new AiReviewResult(readScore(result.path("score")), result.path("review").asString());
    }

    /**
     * score가 누락되면 asInt()가 0을 반환해 0점 게시물이 저장될 수 있다.
     * 누락이나 int로 나타낼 수 없는 값은 null을 돌려주고 ReviewService의 범위 검증이 재시도 대상 예외로 거른다.
     * 정수는 int 범위 안일 때만 읽는다. 소수(85.5 -> 85)와 숫자 문자열("85")은 예전처럼 asInt()로 읽는다.
     * int 범위 밖 정수, 숫자가 아닌 문자열, 불리언, 배열처럼 asInt()가 예외를 던지는 값도 null이다.
     */
    private static Integer readScore(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isIntegralNumber() && !node.canConvertToInt()) {
            return null;
        }
        if (node.isFloatingPointNumber()) {
            double value = node.asDouble();
            if (Double.isNaN(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
                return null;
            }
        }
        try {
            return node.asInt();
        } catch (JacksonException e) {
            return null;
        }
    }

    /** 값이 없거나 비어 있으면 null */
    private static String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asString("");
        return value.isBlank() ? null : value;
    }

    /**
     * 차단에 걸린 안전 등급의 카테고리. blocked=true이거나 확률이 MEDIUM, HIGH인 등급만 모은다.
     */
    private static List<String> flaggedCategories(JsonNode promptFeedback, JsonNode candidate) {
        List<String> categories = new ArrayList<>();
        for (JsonNode ratings : List.of(promptFeedback.path("safetyRatings"), candidate.path("safetyRatings"))) {
            for (JsonNode rating : ratings) {
                String probability = rating.path("probability").asString("");
                boolean flagged = rating.path("blocked").asBoolean(false)
                        || "MEDIUM".equals(probability) || "HIGH".equals(probability);
                String category = textOrNull(rating.path("category"));
                if (flagged && category != null && !categories.contains(category)) {
                    categories.add(category);
                }
            }
        }
        return categories;
    }
}
