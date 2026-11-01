package com.raon.tikitaka.application.territory.policy;

import com.raon.tikitaka.application.territory.policy.TeamStandingCalculator.Standing;
import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.domain.territory.Territory;
import com.raon.tikitaka.domain.user.Users;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TeamStandingCalculatorTest {

    private static Territory land(Long teamId, int price) {
        Territory t = Territory.open(null, null, price, 60);
        if (teamId != null) {
            Location l = mock(Location.class);
            when(l.getLocationId()).thenReturn(teamId);
            t.assign(mock(Users.class), l, LocalDateTime.now());
        }
        return t;
    }

    @Test
    void 땅값_합계로_순위를_매기고_동점은_같은_순위_땅_없는_팀은_0점() {
        List<Standing> s = TeamStandingCalculator.calculate(List.of(
                land(1L, 4000), land(1L, 2500),   // 1팀 6,500 (2개)
                land(2L, 6500),                    // 2팀 6,500 (1개) → 1팀과 동점
                land(3L, 3000),                    // 3팀 3,000
                land(null, 9999)                   // 주인 없는 땅은 어느 팀에도 안 들어감
        ), List.of(1L, 2L, 3L, 4L));

        assertThat(s).extracting(Standing::locationId).containsExactly(1L, 2L, 3L, 4L);   // 동점이면 땅 수 많은 팀 먼저
        assertThat(s).extracting(Standing::rank).containsExactly(1, 1, 3, 4);
        assertThat(s.get(0).totalLandPrice()).isEqualTo(6500);
        assertThat(s.get(0).avgLandPrice()).isEqualTo(3250.0);
        assertThat(s.get(3).ownedDongCount()).isZero();
    }
}
