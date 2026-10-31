package com.raon.tikitaka.application.territory.policy;

import com.raon.tikitaka.application.territory.out.RandomPort;
import com.raon.tikitaka.domain.enums.AssignmentType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.raon.tikitaka.application.territory.policy.AssignmentPolicy.Candidate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssignmentPolicyTest {

    /** 섞지 않고 순서 그대로 두는 난수 */
    static final RandomPort FIXED = new RandomPort() {
        public <T> void shuffle(List<T> list) { }
    };

    @Test
    void 대표는_순위대로_희망_동을_받고_랜덤은_마지막에_배정된다() {
        long 구포1 = 1, 덕천1 = 2, 만덕1 = 3, 화명1 = 4, 금곡 = 5;
        UUID 가은 = UUID.randomUUID(), 나래 = UUID.randomUUID(), 다온 = UUID.randomUUID(),
                라희 = UUID.randomUUID(), 마루 = UUID.randomUUID();

        var result = new AssignmentPolicy(FIXED).assign(List.of(
                new Candidate(가은, List.of(구포1, 덕천1, 만덕1)),
                new Candidate(나래, List.of(구포1, 화명1, 금곡)),
                new Candidate(라희, List.of()),                      // 미등록 → 마지막에 랜덤
                new Candidate(다온, List.of(구포1, 화명1, 덕천1)),
                new Candidate(마루, List.of(만덕1))
        ), List.of(구포1, 덕천1, 만덕1, 화명1, 금곡));

        assertThat(result).extracting(AssignmentPolicy.Assignment::dongId)
                .containsExactly(구포1, 화명1, 금곡, 덕천1, 만덕1);
        assertThat(result).extracting(AssignmentPolicy.Assignment::type).containsExactly(
                AssignmentType.PREFERENCE_1, AssignmentType.PREFERENCE_2, AssignmentType.RANDOM,
                AssignmentType.PREFERENCE_3, AssignmentType.PREFERENCE_1);
    }

    @Test
    void 대표가_동보다_적으면_남는_동은_배정되지_않는다() {
        UUID 가은 = UUID.randomUUID();

        var result = new AssignmentPolicy(FIXED).assign(List.of(new Candidate(가은, List.of())), List.of(1L, 2L, 3L));

        assertThat(result).singleElement().satisfies(a -> {
            assertThat(a.dongId()).isEqualTo(1L);
            assertThat(a.type()).isEqualTo(AssignmentType.RANDOM);
        });
    }

    @Test
    void 대표가_동보다_많으면_거절한다() {
        assertThatThrownBy(() -> new AssignmentPolicy(FIXED).assign(List.of(
                new Candidate(UUID.randomUUID(), List.of()), new Candidate(UUID.randomUUID(), List.of())), List.of(1L)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
