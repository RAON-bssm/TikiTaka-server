package com.raon.tikitaka.adapter.match.out;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gemini 미션 응답의 길이 검증과 대체 문장 생성을 확인한다.
 * WebClient 호출 자체는 대상이 아니고, 응답을 받은 뒤의 판단만 단위로 검증한다.
 */
class GeminiMissionAdapterTest {

    @Test
    void 정상_응답은_그대로_쓸_수_있다() {
        assertThat(GeminiMissionAdapter.isValidMission("동글동글 귀여운 돌멩이 찾아 찍기!")).isTrue();
    }

    @Test
    void 검증_한도인_50자까지는_허용한다() {
        assertThat(GeminiMissionAdapter.isValidMission("가".repeat(GeminiMissionAdapter.MAX_MISSION_LENGTH * 2))).isTrue();
    }

    @Test
    void 응답이_50자를_넘으면_쓸_수_없다() {
        assertThat(GeminiMissionAdapter.isValidMission("가".repeat(GeminiMissionAdapter.MAX_MISSION_LENGTH * 2 + 1))).isFalse();
    }

    @Test
    void 응답이_비어_있으면_쓸_수_없다() {
        assertThat(GeminiMissionAdapter.isValidMission("")).isFalse();
        assertThat(GeminiMissionAdapter.isValidMission("   ")).isFalse();
        assertThat(GeminiMissionAdapter.isValidMission(null)).isFalse();
    }

    @Test
    void 대체_문장은_키워드를_넣은_짧은_문장이다() {
        assertThat(GeminiMissionAdapter.fallbackMission("빨간", "우체통")).isEqualTo("'빨간 우체통' 찾아서 찍기!");
    }
}
