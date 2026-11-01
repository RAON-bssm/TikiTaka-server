package com.raon.tikitaka.application.territory;

import com.raon.tikitaka.application.match.out.StageRepositoryPort;
import com.raon.tikitaka.application.territory.out.DongPreferenceRepositoryPort;
import com.raon.tikitaka.application.territory.out.TerritoryCycleRepositoryPort;
import com.raon.tikitaka.application.user.LocationChangeService;
import com.raon.tikitaka.application.user.out.UserRepositoryPort;
import com.raon.tikitaka.domain.match.Stage;
import com.raon.tikitaka.domain.territory.TerritoryCycle;
import com.raon.tikitaka.domain.user.Users;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CycleAwareLocationChangeServiceTest {

    private final LocationChangeService delegate = mock(LocationChangeService.class);
    private final StageRepositoryPort stagePort = mock(StageRepositoryPort.class);
    private final TerritoryCycleRepositoryPort cyclePort = mock(TerritoryCycleRepositoryPort.class);
    private final UserRepositoryPort userPort = mock(UserRepositoryPort.class);
    private final DongPreferenceRepositoryPort preferencePort = mock(DongPreferenceRepositoryPort.class);
    private final CycleAwareLocationChangeService service =
            new CycleAwareLocationChangeService(delegate, stagePort, cyclePort, userPort, preferencePort);

    private Stage point;
    private Stage battle;

    @BeforeEach
    void setUp() {
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        point = stage(1L, 1, start);
        battle = stage(2L, 2, start.plusDays(7));
        TerritoryCycle cycle = TerritoryCycle.create(point, battle, 4);
        when(cyclePort.findByStageId(any())).thenReturn(Optional.of(cycle));
    }

    private static Stage stage(long id, int round, LocalDateTime startedAt) {
        Stage stage = Stage.create(1, round, startedAt, startedAt.plusDays(7));
        ReflectionTestUtils.setField(stage, "stageId", id);
        return stage;
    }

    @Test
    void 땅따먹기_라운드는_적용하지_않고_처리_완료만_표시한다() {
        when(stagePort.findCurrent(any())).thenReturn(Optional.of(battle));

        service.execute();

        assertThat(battle.isLocationChangeApplied()).isTrue();
        verifyNoInteractions(delegate, preferencePort);
    }

    @Test
    void 포인트_라운드는_이동할_유저의_희망_동을_지우고_위임한다() {
        when(stagePort.findCurrent(any())).thenReturn(Optional.of(point));
        Users mover = mock(Users.class);
        UUID moverId = UUID.randomUUID();
        when(mover.getUserId()).thenReturn(moverId);
        when(userPort.findAllWithPendingLocationChange()).thenReturn(List.of(mover));

        service.execute();

        verify(preferencePort).deleteAllByUserIds(List.of(moverId));
        verify(delegate).execute();
    }
}
