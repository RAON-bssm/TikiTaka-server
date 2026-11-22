package com.raon.tikitaka.application.territory;

import com.raon.tikitaka.application.territory.out.TerritoryCycleRepositoryPort;
import com.raon.tikitaka.application.territory.out.TerritoryPointPort;
import com.raon.tikitaka.application.territory.out.TerritoryQueryPort;
import com.raon.tikitaka.application.territory.out.TerritoryWagePort;
import com.raon.tikitaka.application.territory.policy.WagePolicy;
import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.domain.match.Stage;
import com.raon.tikitaka.domain.territory.Dong;
import com.raon.tikitaka.domain.territory.Territory;
import com.raon.tikitaka.domain.territory.TerritoryCycle;
import com.raon.tikitaka.domain.territory.TerritoryWage;
import com.raon.tikitaka.domain.user.Users;
import com.raon.tikitaka.global.config.TerritoryProperties;
import com.raon.tikitaka.global.config.TerritoryProperties.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class DailyWageServiceTest {

    private static final TerritoryProperties PROPS = new TerritoryProperties(4, 0,
            new Toll(0.15, 1.0), new LandPrice(2500, 0.5, 3.0, 7),
            new Defense(60, 2, 40), new Development(1.65, 0.49, 0.25, 3, 5), new TerritoryProperties.Wage(0.5));
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private final TerritoryCycleRepositoryPort cyclePort = mock(TerritoryCycleRepositoryPort.class);
    private final TerritoryQueryPort territoryPort = mock(TerritoryQueryPort.class);
    private final TerritoryPointPort pointPort = mock(TerritoryPointPort.class);
    private final TerritoryWagePort wagePort = mock(TerritoryWagePort.class);
    private final DailyWageService service = new DailyWageService(cyclePort, territoryPort, pointPort, wagePort,
            new WagePolicy(PROPS));

    /** daysIntoPoint일 전에 포인트 라운드가 시작된 사이클. 8이면 지금은 땅따먹기 2일차 */
    private static TerritoryCycle cycle(int daysIntoPoint) {
        LocalDateTime pointStart = LocalDateTime.now().minusDays(daysIntoPoint);
        Stage point = Stage.create(1, 1, pointStart, pointStart.plusDays(7));
        Stage battle = Stage.create(1, 2, pointStart.plusDays(7), pointStart.plusDays(14));
        return TerritoryCycle.create(point, battle, 4);
    }

    private static Territory land(TerritoryCycle cycle, int price, UUID ownerId) {
        Territory t = Territory.open(cycle, mock(Dong.class), price, 60);
        if (ownerId != null) {
            Users u = mock(Users.class);
            when(u.getUserId()).thenReturn(ownerId);
            t.assign(u, mock(Location.class), LocalDateTime.now());
        }
        return t;
    }

    @Test
    void 땅_주인마다_가진_땅값_합계의_50퍼센트를_주고_주인_없는_땅은_건너뛴다() {
        TerritoryCycle c = cycle(8);
        // land()가 안에서 목을 스터빙하므로 when(...) 밖에서 먼저 만든다(중첩 스터빙 방지)
        List<Territory> lands = List.of(land(c, 2500, A), land(c, 3001, A), land(c, 1250, B), land(c, 7500, null));
        when(cyclePort.findCurrent(any())).thenReturn(Optional.of(c));
        when(territoryPort.findAllByCycleId(any())).thenReturn(lands);

        service.payDailyWage();

        verify(pointPort).add(A, 2750);   // (2500 + 3001) × 0.5 = 2750.5
        verify(pointPort).add(B, 625);
        verifyNoMoreInteractions(pointPort);
        assertThat(c.isWagePaidOn(LocalDate.now())).isTrue();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TerritoryWage>> saved = ArgumentCaptor.forClass(List.class);
        verify(wagePort).saveAll(saved.capture());
        assertThat(saved.getValue())
                .extracting(w -> w.getUser().getUserId(), TerritoryWage::getLandCount,
                        TerritoryWage::getTotalLandPrice, TerritoryWage::getAmount)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(A, 2, 5501L, 2750),
                        org.assertj.core.groups.Tuple.tuple(B, 1, 1250L, 625));
    }

    @Test
    void 같은_날_두_번_돌려도_한_번만_준다() {
        TerritoryCycle c = cycle(8);
        List<Territory> lands = List.of(land(c, 2500, A));
        when(cyclePort.findCurrent(any())).thenReturn(Optional.of(c));
        when(territoryPort.findAllByCycleId(any())).thenReturn(lands);

        service.payDailyWage();
        service.payDailyWage();

        verify(pointPort, times(1)).add(A, 1250);
        verify(wagePort, times(1)).saveAll(any());
    }

    @Test
    void 포인트_라운드에는_주지_않는다() {
        TerritoryCycle c = cycle(5);
        when(cyclePort.findCurrent(any())).thenReturn(Optional.of(c));

        service.payDailyWage();

        verify(pointPort, never()).add(any(), anyInt());
        verifyNoInteractions(territoryPort);
    }
}
