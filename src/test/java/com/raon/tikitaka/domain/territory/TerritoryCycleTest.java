package com.raon.tikitaka.domain.territory;

import com.raon.tikitaka.domain.enums.RoundType;
import com.raon.tikitaka.domain.match.Stage;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TerritoryCycleTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 2, 0, 0);

    private static Stage stage(long id, int round, LocalDateTime startedAt) {
        Stage stage = Stage.create(1, round, startedAt, startedAt.plusDays(7));
        ReflectionTestUtils.setField(stage, "stageId", id);
        return stage;
    }

    private final Stage point = stage(1L, 1, START);
    private final Stage battle = stage(2L, 2, START.plusDays(7));
    private final TerritoryCycle cycle = TerritoryCycle.create(point, battle, 4);

    @Test
    void 포인트_라운드는_4일차_종료부터_동결된다() {
        LocalDateTime freezeAt = START.plusDays(4);
        assertThat(cycle.getRankingFreezeAt()).isEqualTo(freezeAt);
        assertThat(cycle.isRankingFrozen(1L, freezeAt.minusSeconds(1))).isFalse();
        assertThat(cycle.isRankingFrozen(1L, freezeAt)).isTrue();
    }

    @Test
    void 땅따먹기_라운드는_항상_동결이고_사이클_밖_라운드는_동결되지_않는다() {
        assertThat(cycle.isRankingFrozen(2L, START.plusDays(7))).isTrue();
        assertThat(cycle.isRankingFrozen(99L, START.plusDays(5))).isFalse();
        assertThat(cycle.roundTypeOf(2L)).isEqualTo(RoundType.BATTLE);
        assertThat(cycle.roundTypeOf(99L)).isEqualTo(RoundType.NONE);
    }

    @Test
    void 이어지지_않는_라운드는_사이클로_묶을_수_없다() {
        Stage gap = stage(3L, 2, START.plusDays(8));
        assertThatThrownBy(() -> TerritoryCycle.create(point, gap, 4))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
