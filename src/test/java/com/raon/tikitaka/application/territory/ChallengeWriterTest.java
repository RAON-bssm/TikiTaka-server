package com.raon.tikitaka.application.territory;

import com.raon.tikitaka.application.territory.out.ChallengeRepositoryPort;
import com.raon.tikitaka.application.territory.out.TerritoryPointPort;
import com.raon.tikitaka.application.territory.out.TerritoryQueryPort;
import com.raon.tikitaka.application.territory.out.UsedPhotoPort;
import com.raon.tikitaka.application.territory.policy.TollPolicy;
import com.raon.tikitaka.domain.enums.ChallengeResult;
import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.domain.match.Stage;
import com.raon.tikitaka.domain.territory.Dong;
import com.raon.tikitaka.domain.territory.Territory;
import com.raon.tikitaka.domain.territory.TerritoryCycle;
import com.raon.tikitaka.domain.user.Users;
import com.raon.tikitaka.global.config.TerritoryProperties;
import com.raon.tikitaka.global.config.TerritoryProperties.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ChallengeWriterTest {

    private static final TerritoryProperties PROPS = new TerritoryProperties(4, 5,
            new Toll(0.15, 0.90), new LandPrice(2500, 0.5, 3.0, 7),
            new Defense(60, 1, 1, 60), new Development(1.8, 0.49, 250, 3, 5));
    // 소유자 id가 더 커서 '도전자 차감 → 대표 입금' 순서가 된다
    private static final UUID CHALLENGER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final TerritoryQueryPort territoryPort = mock(TerritoryQueryPort.class);
    private final TerritoryPointPort pointPort = mock(TerritoryPointPort.class);
    private final ChallengeRepositoryPort challengePort = mock(ChallengeRepositoryPort.class);
    private final UsedPhotoPort usedPhotoPort = mock(UsedPhotoPort.class);
    private final ChallengeWriter writer = new ChallengeWriter(territoryPort, pointPort, challengePort, usedPhotoPort,
            new TollPolicy(PROPS), PROPS);

    private Territory territory;
    private Users challenger;
    private final Location myTeam = location(2L);

    @BeforeEach
    void setUp() {
        LocalDateTime pointStart = LocalDateTime.now().minusDays(8);   // 지금은 땅따먹기 라운드 2일차
        Stage point = Stage.create(1, 1, pointStart, pointStart.plusDays(7));
        Stage battle = Stage.create(1, 2, pointStart.plusDays(7), pointStart.plusDays(14));
        territory = Territory.open(TerritoryCycle.create(point, battle, 4), mock(Dong.class), 4000, 60);
        challenger = user(CHALLENGER_ID);

        when(territoryPort.findByIdForUpdate(any())).thenReturn(Optional.of(territory));
        when(pointPort.deduct(CHALLENGER_ID, 600)).thenReturn(true);
        when(challengePort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Location location(long id) {
        Location l = mock(Location.class);
        when(l.getLocationId()).thenReturn(id);
        return l;
    }

    private static Users user(UUID id) {
        Users u = mock(Users.class);
        when(u.getUserId()).thenReturn(id);
        return u;
    }

    private void ownedByRival(int defenseScore) {
        territory.assign(user(OWNER_ID), location(1L), LocalDateTime.now());
        territory.setup("파란", "하늘", null, "p0", defenseScore, LocalDateTime.now());
    }

    @Test
    void 이기면_점령하고_통행료_600_중_540이_대표에게_간다() {
        ownedByRival(70);
        var outcome = writer.apply(1L, challenger, myTeam, "p1", "h1", null, null, 70, "좋아요");

        assertThat(outcome.challenge().getResult()).isEqualTo(ChallengeResult.CAPTURED);   // 같으면 도전자 승
        assertThat(territory.isOwnedBy(CHALLENGER_ID)).isTrue();
        verify(pointPort).deduct(CHALLENGER_ID, 600);
        verify(pointPort).add(OWNER_ID, 540);
    }

    @Test
    void 져도_통행료는_내고_땅은_그대로다() {
        ownedByRival(70);
        var outcome = writer.apply(1L, challenger, myTeam, "p1", "h1", null, null, 69, null);

        assertThat(outcome.challenge().getResult()).isEqualTo(ChallengeResult.FAILED);
        assertThat(territory.isOwnedBy(OWNER_ID)).isTrue();
        verify(pointPort).add(OWNER_ID, 540);
    }

    @Test
    void 포인트가_부족하면_아무것도_바뀌지_않는다() {
        ownedByRival(70);
        when(pointPort.deduct(CHALLENGER_ID, 600)).thenReturn(false);

        assertThatThrownBy(() -> writer.apply(1L, challenger, myTeam, "p1", "h1", null, null, 90, null))
                .isInstanceOf(ResponseStatusException.class);
        verify(pointPort, never()).add(any(), anyInt());
        assertThat(territory.isOwnedBy(OWNER_ID)).isTrue();
        verify(challengePort, never()).save(any());
    }

    @Test
    void 주인_없는_땅은_통행료를_내지만_받는_사람이_없다() {
        var outcome = writer.apply(1L, challenger, myTeam, "p1", "h1", null, null, 60, null);

        assertThat(outcome.challenge().getResult()).isEqualTo(ChallengeResult.CAPTURED);
        assertThat(outcome.challenge().getOwnerShare()).isZero();
        verify(pointPort).deduct(CHALLENGER_ID, 600);
        verify(pointPort, never()).add(any(), anyInt());
    }

    @Test
    void 우리_팀_땅에는_도전할_수_없다() {
        territory.assign(user(OWNER_ID), myTeam, LocalDateTime.now());
        assertThatThrownBy(() -> writer.apply(1L, challenger, myTeam, "p1", "h1", null, null, 90, null))
                .isInstanceOf(ResponseStatusException.class);
        verify(pointPort, never()).deduct(any(), anyInt());
    }
}
