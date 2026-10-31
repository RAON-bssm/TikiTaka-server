package com.raon.tikitaka.domain.territory;

import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.domain.user.Users;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TerritoryTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 11, 10, 12, 0);
    private final Territory territory = Territory.open(null, null, 2500, 60);

    private static Location location(long id) {
        Location l = mock(Location.class);
        when(l.getLocationId()).thenReturn(id);
        return l;
    }

    private static Users user() {
        Users u = mock(Users.class);
        when(u.getUserId()).thenReturn(UUID.randomUUID());
        return u;
    }

    @Test
    void 같은_점수면_도전자가_이기고_점령해도_미션은_유지된다() {
        territory.assign(user(), location(1), now);
        territory.setup("파란", "하늘", "파란색이 많을수록", "p1", 78, now);
        territory.requestMissionChange("귀여운", "고양이", null);

        assertThat(territory.isBeatenBy(78)).isTrue();
        assertThat(territory.isBeatenBy(77)).isFalse();

        Users challenger = user();
        territory.capture(challenger, location(2), "p2", 82, now);

        assertThat(territory.isOwnedBy(challenger.getUserId())).isTrue();
        assertThat(territory.isTeam(2L)).isTrue();
        assertThat(territory.getMission()).isEqualTo("파란 하늘");
        assertThat(territory.hasPendingMission()).isFalse();   // 이전 대표의 변경 요청은 버린다
        assertThat(territory.getDefenseScore()).isEqualTo(82);
    }

    @Test
    void 미션_변경은_재촬영해야_적용되고_마지막_점수로_교체된다() {
        territory.assign(user(), location(1), now);
        territory.setup("파란", "하늘", null, "p1", 90, now);
        territory.requestMissionChange("귀여운", "고양이", null);
        assertThat(territory.getMission()).isEqualTo("파란 하늘");

        territory.retake("p2", 70, now);
        assertThat(territory.getMission()).isEqualTo("귀여운 고양이");
        assertThat(territory.getDefenseScore()).isEqualTo(70);
    }

    @Test
    void 감쇠는_하한_아래로_내려가지_않고_개발하면_땅값이_오른다() {
        territory.setup("파란", "하늘", null, "p1", 61, now);
        territory.decay(1, 60);
        territory.decay(1, 60);
        assertThat(territory.getDefenseScore()).isEqualTo(60);

        territory.develop(250);
        assertThat(territory.landPrice()).isEqualTo(2750);
    }

    @Test
    void 주인_없는_땅은_어느_팀도_아니다() {
        assertThat(territory.isOwned()).isFalse();
        assertThat(territory.isTeam(1L)).isFalse();
        assertThat(territory.isChallengeableBy(UUID.randomUUID(), 1L)).isTrue();
    }

    @Test
    void 우리_팀_땅은_도전할_수_없다() {
        territory.assign(user(), location(1), now);
        assertThat(territory.isChallengeableBy(UUID.randomUUID(), 1L)).isFalse();
        assertThat(territory.isChallengeableBy(UUID.randomUUID(), 2L)).isTrue();
    }
}
