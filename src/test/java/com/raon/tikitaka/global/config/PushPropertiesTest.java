package com.raon.tikitaka.global.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PushPropertiesTest {

    @Test
    void 비어_있으면_기본_문구를_쓴다() {
        PushProperties p = new PushProperties("", " ", null, "");

        assertThat(p.title()).isEqualTo(PushProperties.DEFAULT_TITLE);
        assertThat(p.dailyWageBody()).isEqualTo(PushProperties.DEFAULT_DAILY_WAGE_BODY);
        assertThat(p.territoryCapturedBody()).isEqualTo(PushProperties.DEFAULT_TERRITORY_CAPTURED_BODY);
        assertThat(p.territoryCapturedBodyNoDong()).isEqualTo(PushProperties.DEFAULT_TERRITORY_CAPTURED_BODY_NO_DONG);
    }

    @Test
    void 땅_뺏김_문구에_자리표시자가_빠지면_기동을_막는다() {
        assertThatThrownBy(() -> new PushProperties(null, null, "{challenger}에게 땅을 뺏겼습니다", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PUSH_TERRITORY_CAPTURED_BODY");
        assertThatThrownBy(() -> new PushProperties(null, null, "{challnger}에게 {dong} 땅을 뺏겼습니다", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 동_없는_문구에_dong을_쓰면_기동을_막는다() {
        assertThatThrownBy(() -> new PushProperties(null, null, null, "{challenger}에게 {dong} 땅을 뺏겼습니다"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PUSH_TERRITORY_CAPTURED_BODY_NO_DONG");
    }
}
