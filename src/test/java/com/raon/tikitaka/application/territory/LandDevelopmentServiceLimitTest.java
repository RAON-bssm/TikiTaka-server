package com.raon.tikitaka.application.territory;

import com.raon.tikitaka.application.territory.out.*;
import com.raon.tikitaka.application.territory.policy.DevelopmentPolicy;
import com.raon.tikitaka.application.territory.view.DevelopResultView;
import com.raon.tikitaka.application.user.out.UserRepositoryPort;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class LandDevelopmentServiceLimitTest {

    private static final UUID ME = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final long DONG_CODE = 2626010100L;

    private final TerritoryCycleRepositoryPort cyclePort = mock(TerritoryCycleRepositoryPort.class);
    private final TerritoryQueryPort territoryPort = mock(TerritoryQueryPort.class);
    private final UserRepositoryPort userPort = mock(UserRepositoryPort.class);
    private final TerritoryPointPort pointPort = mock(TerritoryPointPort.class);
    private final LandDevelopmentPort developmentPort = mock(LandDevelopmentPort.class);
    private final RandomPort randomPort = mock(RandomPort.class);

    private static TerritoryProperties props(int dailyLimitPerUser, int dailyGainLimitPerDong) {
        return new TerritoryProperties(4, 0, new Toll(0.15, 1.0), new LandPrice(2500, 0.5, 3.0, 7),
                new Defense(60, 2, 40), new Development(1.65, 0.49, 0.25, dailyLimitPerUser, dailyGainLimitPerDong),
                new TerritoryProperties.Wage(0.5));
    }

    private LandDevelopmentService service(TerritoryProperties p) {
        return new LandDevelopmentService(cyclePort, territoryPort, userPort, pointPort, developmentPort, randomPort,
                new DevelopmentPolicy(p), p);
    }

    @BeforeEach
    void setUp() {
        LocalDateTime pointStart = LocalDateTime.now().minusDays(8);   // 땅따먹기 2일차
        TerritoryCycle cycle = TerritoryCycle.create(Stage.create(1, 1, pointStart, pointStart.plusDays(7)),
                Stage.create(1, 2, pointStart.plusDays(7), pointStart.plusDays(14)), 4);
        Location team = mock(Location.class);
        when(team.getLocationId()).thenReturn(1L);
        Users owner = mock(Users.class);
        when(owner.getUserId()).thenReturn(OWNER);
        Territory land = Territory.open(cycle, mock(Dong.class), 2500, 60);
        land.assign(owner, team, LocalDateTime.now());
        Users me = mock(Users.class);
        when(me.getMainLocation()).thenReturn(team);

        when(cyclePort.findCurrent(any())).thenReturn(Optional.of(cycle));
        when(territoryPort.findIdByCycleIdAndDongCode(any(), eq(DONG_CODE))).thenReturn(Optional.of(1L));
        when(territoryPort.findByIdForUpdate(1L)).thenReturn(Optional.of(land));
        when(userPort.findByIdWithLocations(ME)).thenReturn(Optional.of(me));
        when(pointPort.deduct(eq(ME), anyInt())).thenReturn(true);
        when(randomPort.nextDouble()).thenReturn(0.1);   // 성공
    }

    @Test
    void 개인_제한이_0이면_시도_횟수를_세지_않고_남은_횟수는_마이너스1() {
        when(developmentPort.countSuccessByTerritorySince(any(), any())).thenReturn(0L);

        DevelopResultView result = service(props(0, 3)).develop(ME, DONG_CODE);

        assertThat(result.success()).isTrue();
        assertThat(result.remainingToday()).isEqualTo(DevelopResultView.UNLIMITED);
        assertThat(result.cost()).isEqualTo(4125);   // 2,500 × 165%
        assertThat(result.teamGain()).isEqualTo(625);   // 2,500 × 25%
        assertThat(result.landPrice()).isEqualTo(3125);
        assertThat(result.nextCost()).isEqualTo(5156);  // 3,125 × 165% = 5,156.25
        verify(developmentPort, never()).countByUserSince(any(), any());
    }

    @Test
    void 그_땅이_오늘_3번_성공했으면_더_개발할_수_없다() {
        when(developmentPort.countSuccessByTerritorySince(any(), any())).thenReturn(3L);

        assertThatThrownBy(() -> service(props(0, 3)).develop(ME, DONG_CODE))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(pointPort, never()).deduct(any(), anyInt());
    }

    @Test
    void 개인_제한을_다시_켜면_기존처럼_막는다() {
        when(developmentPort.countByUserSince(eq(ME), any())).thenReturn(3L);

        assertThatThrownBy(() -> service(props(3, 3)).develop(ME, DONG_CODE))
                .isInstanceOf(ResponseStatusException.class);
        verify(pointPort, never()).deduct(any(), anyInt());
    }
}
