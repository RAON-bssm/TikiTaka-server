package com.raon.tikitaka.adapter.review.out;

import java.util.Set;

/**
 * Gemini 안전 필터 차단으로 볼 값 모음. 값은 Gemini API 레퍼런스(generateContent)의
 * PromptFeedback.BlockReason과 Candidate.FinishReason 정의를 기준으로 했다.
 * 같은 이미지와 내용은 다시 보내도 똑같이 막히므로, 이 값으로 끝난 응답은 재시도하지 않고 거절한다.
 */
final class GeminiSafetyBlock {

    /**
     * 프롬프트(이미지·내용) 자체가 막힌 이유. 이 값이 오면 후보 없이 응답한다.
     * OTHER("알 수 없는 이유")와 BLOCK_REASON_UNSPECIFIED는 안전 필터라고 볼 근거가 없어 넣지 않았다.
     */
    static final Set<String> BLOCK_REASONS = Set.of(
            "SAFETY",
            "BLOCKLIST",
            "PROHIBITED_CONTENT",
            "IMAGE_SAFETY"
    );

    /**
     * 후보 생성이 안전 사유로 멈춘 경우. RECITATION(저작권), MAX_TOKENS(길이), LANGUAGE, OTHER,
     * 계정 제한(PUP_LIMITED_DISABLED)처럼 글 내용의 안전 문제와 무관한 값은 넣지 않았다.
     */
    static final Set<String> FINISH_REASONS = Set.of(
            "SAFETY",
            "BLOCKLIST",
            "PROHIBITED_CONTENT",
            "SPII",
            "IMAGE_SAFETY",
            "IMAGE_PROHIBITED_CONTENT"
    );

    private GeminiSafetyBlock() {
    }
}
