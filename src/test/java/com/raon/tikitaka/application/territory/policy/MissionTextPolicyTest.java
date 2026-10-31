package com.raon.tikitaka.application.territory.policy;

import com.raon.tikitaka.application.territory.out.WordFilterPort;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MissionTextPolicyTest {

    private static final List<String> BANNED = List.of("시발", "병신", "좆", "씹");
    private static final List<String> ALLOWED = List.of("시발점", "시바견");

    private static boolean banned(String text) {
        return MissionTextPolicy.containsBannedWord(text, BANNED, ALLOWED);
    }

    @Test
    void 발음_뼈대가_같은_변형을_모두_막는다() {
        assertThat(List.of("시발", "씨발", "싯발", "씨바", "싯바", "씌발", "시 발", "시1발", "뼝신")).allMatch(MissionTextPolicyTest::banned);
    }

    @Test
    void 예외_단어와_한_글자_금칙어의_오탐은_막지_않는다() {
        assertThat(banned("시발점")).isFalse();
        assertThat(banned("귀여운시바견")).isFalse();
        assertThat(banned("조개")).isFalse();    // '좆'을 뼈대로 비교했다면 걸렸을 단어
        assertThat(banned("십자가")).isFalse();  // '씹'도 마찬가지
        assertThat(banned("좆같은")).isTrue();
    }

    @Test
    void 키워드는_공백_없는_한글_1에서_10자만_받는다() {
        WordFilterPort port = new WordFilterPort() {
            public List<String> findAllBannedWords() { return BANNED; }
            public List<String> findAllAllowedWords() { return ALLOWED; }
        };
        MissionTextPolicy policy = new MissionTextPolicy(port);

        policy.validate("파란", "하늘", "파란색이 넓을수록 고득점");
        for (String[] bad : new String[][]{{"ㅅㅂ", "하늘"}, {"파란1", "하늘"}, {"파 란", "하늘"}, {"blue", "하늘"},
                {"파란", "하늘하늘하늘하늘하늘하"}, {"", "하늘"}}) {
            assertThatThrownBy(() -> policy.validate(bad[0], bad[1], null)).isInstanceOf(ResponseStatusException.class);
        }
        assertThatThrownBy(() -> policy.validate("파란", "하늘", "가".repeat(51))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> policy.validate("파란", "하늘", "씨발 같은 기준")).isInstanceOf(ResponseStatusException.class);
    }
}
