package com.raon.tikitaka.application.territory.policy;

import com.raon.tikitaka.application.territory.out.RandomPort;
import com.raon.tikitaka.domain.enums.AssignmentType;
import com.raon.tikitaka.global.config.TerritoryProperties;
import com.raon.tikitaka.global.config.TerritoryProperties.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.raon.tikitaka.application.territory.policy.AssignmentPolicy.Candidate;
import static org.assertj.core.api.Assertions.assertThat;

class TerritoryPolicyTest {

    static final TerritoryProperties PROPS = new TerritoryProperties(4, 5,
            new Toll(0.15, 0.90), new LandPrice(2500, 0.5, 3.0, 7),
            new Defense(60, 1, 1, 60), new Development(1.8, 0.49, 250, 3, 5));

    /** 섞지 않고 순서 그대로 두는 난수 */
    static final RandomPort FIXED = new RandomPort() {
        public double nextDouble() { return 0.5; }
        public <T> void shuffle(List<T> list) { }
    };

    @Test
    void 통행료와_대표_몫은_소수점을_버린다() {
        TollPolicy toll = new TollPolicy(PROPS);
        assertThat(toll.toll(4000)).isEqualTo(600);
        assertThat(toll.ownerShare(600)).isEqualTo(540);
        assertThat(toll.toll(2999)).isEqualTo(449);   // 449.85
    }

    @Test
    void 인기도는_범위로_자르고_보드_평균이_0이면_1이다() {
        LandPricePolicy price = new LandPricePolicy(PROPS);
        assertThat(price.popularity(10, 0)).isEqualTo(1.0);
        assertThat(price.popularity(100, 2)).isEqualTo(3.0);
        assertThat(price.popularity(0, 2)).isEqualTo(0.5);
        assertThat(price.basePrice(3, 2)).isEqualTo(3750);   // 2500 × 1.5
    }

    @Test
    void 개발은_확률_미만의_난수면_성공한다() {
        DevelopmentPolicy dev = new DevelopmentPolicy(PROPS);
        assertThat(dev.cost(4000)).isEqualTo(7200);
        assertThat(dev.isSuccess(0.4899)).isTrue();
        assertThat(dev.isSuccess(0.49)).isFalse();
    }

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
}
