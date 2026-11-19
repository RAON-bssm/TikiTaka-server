package com.raon.tikitaka.application.appversion.policy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppVersionTest {

    @ParameterizedTest
    @ValueSource(strings = {"1", "1.3", "1.3.0", "0.0.1", "999999.999999.999999", "01.2.3"})
    void 숫자_한_자리에서_세_자리까지_허용한다(String value) {
        assertThat(AppVersion.isValid(value)).isTrue();
        assertThat(AppVersion.tryParse(value)).isPresent();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abc", "1.2.3.4", " ", "-1.0.0", "1.-2.0", "+1.0.0", "1000000.0.0", "1.0.1234567",
            "1.", ".1", "1..2", "v1.2.3", "1.2.3-beta", " 1.2.3", "1.2.3 ", "1,2,3", "１.２.３"})
    void 형식이_틀리면_거부한다(String value) {
        assertThat(AppVersion.isValid(value)).isFalse();
        assertThat(AppVersion.tryParse(value)).isEmpty();
        assertThatThrownBy(() -> AppVersion.parse(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 자리별_숫자로_비교해서_1_10_0이_1_9_0보다_크다() {
        assertThat(AppVersion.parse("1.10.0")).isGreaterThan(AppVersion.parse("1.9.0"));
        assertThat(AppVersion.parse("1.9.0").isLowerThan(AppVersion.parse("1.10.0"))).isTrue();
        assertThat(AppVersion.parse("2.0.0")).isGreaterThan(AppVersion.parse("1.99.99"));
        assertThat(AppVersion.parse("1.2.10")).isGreaterThan(AppVersion.parse("1.2.9"));
    }

    @Test
    void 빠진_자리는_0이라_1_3과_1_3_0은_같다() {
        assertThat(AppVersion.parse("1.3")).isEqualTo(AppVersion.parse("1.3.0"));
        assertThat(AppVersion.parse("1.3")).isEqualByComparingTo(AppVersion.parse("1.3.0"));
        assertThat(AppVersion.parse("2")).isEqualTo(new AppVersion(2, 0, 0));
        assertThat(AppVersion.parse("1.03.0")).isEqualTo(AppVersion.parse("1.3.0"));
    }

    @Test
    void 문자열은_세_자리로_정규화한다() {
        assertThat(AppVersion.parse("1.3")).hasToString("1.3.0");
    }
}
