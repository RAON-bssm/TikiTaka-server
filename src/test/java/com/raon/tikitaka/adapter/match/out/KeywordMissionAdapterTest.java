package com.raon.tikitaka.adapter.match.out;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordMissionAdapterTest {

    private final KeywordMissionAdapter adapter = new KeywordMissionAdapter();

    @Test
    void 형용사와_명사를_공백_하나로_이어_붙인다() {
        assertThat(adapter.generate("빨간", "우체통")).isEqualTo("빨간 우체통");
    }

    @Test
    void 키워드_앞뒤_공백은_제거한다() {
        assertThat(adapter.generate(" 빨간 ", " 우체통 ")).isEqualTo("빨간 우체통");
    }
}
