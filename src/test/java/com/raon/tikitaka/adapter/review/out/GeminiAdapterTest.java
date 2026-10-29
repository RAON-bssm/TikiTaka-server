package com.raon.tikitaka.adapter.review.out;

import com.raon.tikitaka.application.review.AiReviewInvalidResponseException;
import com.raon.tikitaka.application.review.AiReviewRejectedException;
import com.raon.tikitaka.application.review.AiReviewResult;
import com.raon.tikitaka.application.review.AiReviewRetryableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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

    @ParameterizedTest
    @ValueSource(ints = {400, 422})
    void 그_밖의_4xx는_거절(int status) {
        RuntimeException e = adapter.classify(error(status, new HttpHeaders(), ""));

        assertThat(e).isInstanceOfSatisfying(AiReviewRejectedException.class,
                r -> assertThat(r.getStatus()).isEqualTo(status));
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 404, 408, 429, 500, 503})
    void 키_설정_오류와_요청_시간_초과도_재시도_대상(int status) {
        // 401, 403(키 만료, 권한 설정), 404(모델 설정)와 408은 글 내용 탓이 아니라 거절로 저장하지 않는다
        assertThat(adapter.classify(error(status, new HttpHeaders(), "")))
                .isInstanceOf(AiReviewRetryableException.class)
                .isNotInstanceOf(AiReviewRejectedException.class);
    }

    @Test
    void 응답_401도_Gemini가_준_대기_시간을_담는다() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Retry-After", "30");

        assertThat(adapter.classify(error(401, headers, ""))).isInstanceOfSatisfying(AiReviewRetryableException.class,
                r -> assertThat(r.getRetryAfter()).isEqualTo(Duration.ofSeconds(30)));
    }

    // ===== 재시도 대기 시간 상한 =====

    @ParameterizedTest
    @CsvSource({"1e309, ", "NaN, ", "Infinity, ", "-5, ", "soon, ", "99999999999, 3600000", "3601, 3600000",
            "17, 17000", "0.5, 500", "0, 0"})
    void Retry_After_헤더는_유한한_0_이상으로_읽고_1시간으로_자른다(String header, Long expectedMillis) {
        Duration parsed = GeminiAdapter.parseRetryAfterHeader(header);

        assertThat(parsed == null ? null : parsed.toMillis()).isEqualTo(expectedMillis);
    }

    @ParameterizedTest
    @CsvSource({"1e309s, ", "NaNs, ", "-5s, ", "abcs, ", "99999999999s, 3600000", "3601s, 3600000",
            "17s, 17000", "0.5s, 500"})
    void 본문_retryDelay도_유한한_0_이상으로_읽고_1시간으로_자른다(String delay, Long expectedMillis) {
        String body = "{\"error\": {\"details\": [{\"retryDelay\": \"" + delay + "\"}]}}";

        Duration parsed = adapter.parseRetryDelay(body);

        assertThat(parsed == null ? null : parsed.toMillis()).isEqualTo(expectedMillis);
    }

    @Test
    void 아주_먼_HTTP_날짜도_1시간으로_자른다() {
        assertThat(GeminiAdapter.parseRetryAfterHeader("Fri, 31 Dec 9999 23:59:59 GMT"))
                .isEqualTo(GeminiAdapter.MAX_RETRY_AFTER);
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

    @ParameterizedTest
    @CsvSource({"100, 100", "0, 0", "85.5, 85", "'\"85\"', 85"})
    void 범위_안의_정수와_소수와_숫자_문자열은_예전처럼_읽는다(String score, int expected) {
        assertThat(adapter.parse(response("{\"score\": " + score + "}")).score()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"4294967396", "1e20", "\"abc\"", "true", "[]"})
    void int로_나타낼_수_없는_점수는_null로_돌려_검증에_맡긴다(String score) {
        // 예전에는 asInt()가 예외를 던져 분류되지 않은 오류로 새거나, 잘리면 범위 검사를 통과할 수 있었다
        assertThat(adapter.parse(response("{\"score\": " + score + "}")).score()).isNull();
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

    // ===== 안전 필터 =====

    @Test
    void 프롬프트가_안전_필터에_막혀_후보가_없으면_거절() {
        String body = """
                {"promptFeedback": {"blockReason": "SAFETY", "safetyRatings": [
                  {"category": "HARM_CATEGORY_DANGEROUS_CONTENT", "probability": "HIGH", "blocked": true},
                  {"category": "HARM_CATEGORY_HARASSMENT", "probability": "NEGLIGIBLE"}
                ]}}
                """;

        assertThatThrownBy(() -> adapter.parse(body))
                .isInstanceOf(AiReviewRejectedException.class)
                .isNotInstanceOf(AiReviewRetryableException.class);
    }

    @Test
    void 후보가_안전_사유로_멈추고_텍스트가_없으면_거절() {
        String body = """
                {"candidates": [{"finishReason": "PROHIBITED_CONTENT",
                  "safetyRatings": [{"category": "HARM_CATEGORY_SEXUALLY_EXPLICIT", "probability": "MEDIUM"}]}]}
                """;

        assertThatThrownBy(() -> adapter.parse(body)).isInstanceOf(AiReviewRejectedException.class);
    }

    @Test
    void 안전_사유가_아닌_blockReason은_재시도_대상() {
        // OTHER는 "알 수 없는 이유"라 안전 필터로 보지 않는다
        assertThatThrownBy(() -> adapter.parse("{\"promptFeedback\": {\"blockReason\": \"OTHER\"}}"))
                .isInstanceOf(AiReviewInvalidResponseException.class);
    }

    @Test
    void 후보도_blockReason도_없는_응답은_재시도_대상() {
        assertThatThrownBy(() -> adapter.parse("{\"candidates\": []}"))
                .isInstanceOf(AiReviewInvalidResponseException.class);
        assertThatThrownBy(() -> adapter.parse("{}"))
                .isInstanceOf(AiReviewInvalidResponseException.class);
    }

    @Test
    void 안전과_무관한_finishReason으로_텍스트가_없으면_재시도_대상() {
        for (String reason : new String[]{"MAX_TOKENS", "RECITATION", "OTHER"}) {
            String body = "{\"candidates\": [{\"finishReason\": \"" + reason + "\", \"content\": {\"parts\": []}}]}";
            assertThatThrownBy(() -> adapter.parse(body))
                    .as(reason)
                    .isInstanceOf(AiReviewInvalidResponseException.class);
        }
    }

    @Test
    void 텍스트가_있으면_finishReason이_안전_사유여도_그대로_읽는다() {
        String body = JsonMapper.builder().build().writeValueAsString(Map.of("candidates", List.of(Map.of(
                "finishReason", "SAFETY",
                "content", Map.of("parts", List.of(Map.of("text", "{\"score\": 60, \"review\": \"보통\"}")))))));

        assertThat(adapter.parse(body).score()).isEqualTo(60);
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
