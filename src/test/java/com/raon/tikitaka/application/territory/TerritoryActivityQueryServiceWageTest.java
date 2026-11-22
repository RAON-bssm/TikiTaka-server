package com.raon.tikitaka.application.territory;

import com.raon.tikitaka.application.territory.out.*;
import com.raon.tikitaka.application.territory.view.WageHistoryView;
import com.raon.tikitaka.domain.match.Stage;
import com.raon.tikitaka.domain.territory.TerritoryCycle;
import com.raon.tikitaka.domain.territory.TerritoryWage;
import com.raon.tikitaka.domain.user.Users;
import com.raon.tikitaka.global.config.TerritoryProperties;
import com.raon.tikitaka.global.config.TerritoryProperties.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TerritoryActivityQueryServiceWageTest {

    private static final TerritoryProperties PROPS = new TerritoryProperties(4, 0,
            new Toll(0.15, 1.0), new LandPrice(2500, 0.5, 3.0, 7),
            new Defense(60, 2, 40), new Development(1.65, 0.49, 0.25, 3, 5), new TerritoryProperties.Wage(0.5));
    private static final UUID ME = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    private final TerritoryWagePort wagePort = mock(TerritoryWagePort.class);
    private final TerritoryActivityQueryService service = new TerritoryActivityQueryService(
            mock(TerritoryCycleRepositoryPort.class), mock(TerritoryQueryPort.class), mock(LandDevelopmentPort.class),
            mock(BlockedUserPort.class), wagePort, PROPS);

    private static TerritoryWage wage(LocalDate day, int lands, long total, int amount) {
        LocalDateTime start = LocalDateTime.now().minusDays(8);
        TerritoryCycle cycle = TerritoryCycle.create(Stage.create(1, 1, start, start.plusDays(7)),
                Stage.create(1, 2, start.plusDays(7), start.plusDays(14)), 4);
        return TerritoryWage.of(cycle, mock(Users.class), day, lands, total, 0.5, amount, LocalDateTime.now());
    }

    @Test
    void 사이클을_안_주면_전체_이력을_주고_합계를_더한다() {
        LocalDate today = LocalDate.now();
        when(wagePort.findRecentByUser(ME, 100)).thenReturn(List.of(
                wage(today, 2, 5501, 2750), wage(today.minusDays(1), 1, 2500, 1250)));

        WageHistoryView view = service.getWages(ME, null);

        assertThat(view.totalAmount()).isEqualTo(4000);
        assertThat(view.wages()).extracting(WageHistoryView.Item::paidOn).containsExactly(today, today.minusDays(1));
        assertThat(view.wages().get(0).landCount()).isEqualTo(2);
        verify(wagePort, never()).findRecentByUserAndCycle(any(), any(), anyInt());
    }

    @Test
    void 사이클을_주면_그_사이클만_조회한다() {
        when(wagePort.findRecentByUserAndCycle(ME, 7L, 100)).thenReturn(List.of());

        WageHistoryView view = service.getWages(ME, 7L);

        assertThat(view.totalAmount()).isZero();
        assertThat(view.wages()).isEmpty();
        verify(wagePort, never()).findRecentByUser(any(), anyInt());
    }
}
