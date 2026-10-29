package com.raon.tikitaka.adapter.review.out;

import com.raon.tikitaka.application.review.AiReviewInvalidResponseException;
import com.raon.tikitaka.application.review.AiReviewRejectedException;
import com.raon.tikitaka.application.review.AiReviewResult;
import com.raon.tikitaka.application.review.AiReviewRetryableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Gemini 실패 분류와 응답 파싱. 실제 호출 없이 WebClientResponseException과 응답 본문 문자열로 확인한다.
 */
class GeminiAdapterTest {

    private final GeminiAdapter adapter = new GeminiAdapter(JsonMapper.builder().build());

    // ===== 실패 분류 =====

    @Test
    void 응답_429는_재시도_대상이고_Retry_After_헤더를_담는다() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Retry-After", "42");

        RuntimeException e = adapter.classify(error(429, headers, ""));

        assertThat(e).isInstanceOfSatisfying(AiReviewRetryableException.class,
                r -> assertThat(r.getRetryAfter()).isEqualTo(Duration.ofSeconds(42)));
    }

    @Test
    void 응답_429에_헤더가_없으면_본문의_retryDelay를_쓴다() {
        String body = """
                {"error": {"code": 429, "details": [
                  {"@type": "type.googleapis.com/google.rpc.QuotaFailure"},
                  {"@type": "type.googleapis.com/google.rpc.RetryInfo", "retryDelay": "17s"}
                ]}}
                """;

        RuntimeException e = adapter.classify(error(429, new HttpHeaders(), body));

        assertThat(e).isInstanceOfSatisfying(AiReviewRetryableException.class,
                r -> assertThat(r.getRetryAfter()).isEqualTo(Duration.ofSeconds(17)));
    }

    @Test
    void 응답_429에_대기_시간이_없으면_retryAfter는_null() {
        RuntimeException e = adapter.classify(error(429, new HttpHeaders(), "not json"));

        assertThat(e).isInstanceOfSatisfying(AiReviewRetryableException.class,
                r -> assertThat(r.getRetryAfter()).isNull());
    }

    @Test
    void 응답_5xx는_재시도_대상() {
        assertThat(adapter.classify(error(500, new HttpHeaders(), ""))).isInstanceOf(AiReviewRetryableException.class);
        assertThat(adapter.classify(error(503, new HttpHeaders(), ""))).isInstanceOf(AiReviewRetryableException.class);
    }

    @Test
    void 응답_429를_뺀_4xx는_거절() {
        RuntimeException e = adapter.classify(error(400, new HttpHeaders(), ""));

        assertThat(e).isInstanceOfSatisfying(AiReviewRejectedException.class,
                r -> assertThat(r.getStatus()).isEqualTo(400));
    }

    @Test
    void Retry_After는_숫자와_HTTP_날짜를_읽고_나머지는_null() {
        assertThat(GeminiAdapter.parseRetryAfterHeader("0")).isEqualTo(Duration.ZERO);
        assertThat(GeminiAdapter.parseRetryAfterHeader("Wed, 21 Oct 2015 07:28:00 GMT")).isEqualTo(Duration.ZERO);
        assertThat(GeminiAdapter.parseRetryAfterHeader("soon")).isNull();
        assertThat(GeminiAdapter.parseRetryAfterHeader(null)).isNull();
    }

    // ===== 응답 파싱 =====

    @Test
    void 정상_응답이면_점수와_리뷰를_읽는다() {
        AiReviewResult result = adapter.parse(response("{\"score\": 77, \"review\": \"좋아요\"}"));

        assertThat(result.score()).isEqualTo(77);
        assertThat(result.review()).isEqualTo("좋아요");
    }

    @Test
    void 점수가_없으면_null로_돌려_검증에_맡긴다() {
        assertThat(adapter.parse(response("{\"review\": \"좋아요\"}")).score()).isNull();
    }

    @Test
    void 심사_텍스트가_JSON이_아니면_형식_오류() {
        assertThatThrownBy(() -> adapter.parse(response("점수는 77점")))
                .isInstanceOf(AiReviewInvalidResponseException.class);
    }

    @Test
    void 후보가_없는_응답은_형식_오류() {
        // 안전 필터로 막히면 candidates 없이 promptFeedback만 온다
        assertThatThrownBy(() -> adapter.parse("{\"promptFeedback\": {\"blockReason\": \"SAFETY\"}}"))
                .isInstanceOf(AiReviewInvalidResponseException.class);
    }

    @Test
    void 본문이_JSON이_아니면_형식_오류() {
        assertThatThrownBy(() -> adapter.parse("<html>"))
                .isInstanceOf(AiReviewInvalidResponseException.class);
    }

    private static WebClientResponseException error(int status, HttpHeaders headers, String body) {
        return WebClientResponseException.create(status, "error", headers, body.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
    }

    /** Gemini generateContent 응답 모양. 심사 결과는 parts[0].text에 JSON 문자열로 들어 있다 */
    private static String response(String text) {
        return JsonMapper.builder().build().writeValueAsString(
                Map.of("candidates", List.of(Map.of("content", Map.of("parts", List.of(Map.of("text", text)))))));
    }
}
