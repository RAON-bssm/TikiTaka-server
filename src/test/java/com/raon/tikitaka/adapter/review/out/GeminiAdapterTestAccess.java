package com.raon.tikitaka.adapter.review.out;

import com.raon.tikitaka.application.review.AiReviewResult;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

/**
 * 다른 패키지 테스트(심사 처리 테스트)에서 실제 Gemini 응답 해석을 거치게 하는 용도.
 */
public final class GeminiAdapterTestAccess {

    private static final GeminiAdapter ADAPTER = new GeminiAdapter(JsonMapper.builder().build());

    private GeminiAdapterTestAccess() {
    }

    public static AiReviewResult parse(String responseBody) {
        return ADAPTER.parse(responseBody);
    }

    /** Gemini가 status로 응답했을 때 어댑터가 던지는 예외 */
    public static RuntimeException classify(int status) {
        return ADAPTER.classify(WebClientResponseException.create(status, "error", new HttpHeaders(), new byte[0],
                StandardCharsets.UTF_8));
    }
}
