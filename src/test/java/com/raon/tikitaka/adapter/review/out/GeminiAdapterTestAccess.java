package com.raon.tikitaka.adapter.review.out;

import com.raon.tikitaka.application.review.AiReviewResult;
import tools.jackson.databind.json.JsonMapper;

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
}
