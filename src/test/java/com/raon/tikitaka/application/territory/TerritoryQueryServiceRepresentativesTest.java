package com.raon.tikitaka.application.territory;

import com.raon.tikitaka.application.territory.out.TerritoryCycleRepositoryPort;
import com.raon.tikitaka.application.territory.out.TerritoryQueryPort;
import com.raon.tikitaka.application.territory.view.RepresentativesView;
import com.raon.tikitaka.application.user.out.UserRepositoryPort;
import com.raon.tikitaka.domain.enums.AssignmentType;
import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.domain.match.Stage;
import com.raon.tikitaka.domain.territory.Dong;
import com.raon.tikitaka.domain.territory.Representative;
import com.raon.tikitaka.domain.territory.TerritoryCycle;
import com.raon.tikitaka.domain.user.Users;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 선발 결과 조회(임시 API). 사이클 고르기, 선발 전 빈 목록, mine 표시를 본다.
 * 정렬과 탈퇴·차단 유저 제외는 쿼리가 하므로 RepresentativeQueryDbTest에서 본다.
 */
class TerritoryQueryServiceRepresentativesTest {

    private final TerritoryCycleRepositoryPort cyclePort = mock(TerritoryCycleRepositoryPort.class);
    private final TerritoryQueryPort queryPort = mock(TerritoryQueryPort.class);
    private final TerritoryQueryService service =
            new TerritoryQueryService(cyclePort, queryPort, mock(UserRepositoryPort.class));

    private final UUID me = UUID.randomUUID();
    private TerritoryCycle cycle;

    @BeforeEach
    void setUp() {
        LocalDateTime start = LocalDateTime.now().minusDays(5);
        cycle = TerritoryCycle.create(stage(1L, 1, start), stage(2L, 2, start.plusDays(7)), 4);
        ReflectionTestUtils.setField(cycle, "cycleId", 10L);
        lenient().when(cyclePort.findCurrent(any())).thenReturn(Optional.of(cycle));
        lenient().when(cyclePort.findById(10L)).thenReturn(Optional.of(cycle));
    }

    @Test
    void 선발_전이면_빈_목록을_주고_대표를_조회하지_않는다() {
        RepresentativesView view = service.getRepresentatives(me, null, null);

        assertThat(view.cycleId()).isEqualTo(10L);
        assertThat(view.representativesSelected()).isFalse();
        assertThat(view.representatives()).isEmpty();
        verifyNoInteractions(queryPort);
    }

    @Test
    void 선발_후에는_쿼리_순서대로_돌려주고_내가_대표면_mine이_true다() {
        cycle.markRepresentativesSelected();
        UUID other = UUID.randomUUID();
        List<Representative> rows = List.of(
                representative(other, "가은", 100L, 1, 900L, 2635051000L, AssignmentType.PREFERENCE_1),
                representative(me, "나래", 100L, 2, 800L, 2635052000L, AssignmentType.RANDOM),
                representative(UUID.randomUUID(), "다온", 200L, 1, 700L, 2650051000L, AssignmentType.PREFERENCE_2));
        when(queryPort.findRepresentatives(10L, null, me)).thenReturn(rows);

        RepresentativesView view = service.getRepresentatives(me, null, null);

        assertThat(view.representativesSelected()).isTrue();
        assertThat(view.representatives())
                .extracting(RepresentativesView.Item::locationId, RepresentativesView.Item::rank,
                        RepresentativesView.Item::userName, RepresentativesView.Item::dongCode,
                        RepresentativesView.Item::assignedBy, RepresentativesView.Item::mine)
                .containsExactly(
                        tuple(100L, 1, "가은", 2635051000L, AssignmentType.PREFERENCE_1, false),
                        tuple(100L, 2, "나래", 2635052000L, AssignmentType.RANDOM, true),
                        tuple(200L, 1, "다온", 2650051000L, AssignmentType.PREFERENCE_2, false));
    }

    @Test
    void locationId와_cycleId를_넘기면_그_사이클과_구로_조회한다() {
        cycle.markRepresentativesSelected();
        when(queryPort.findRepresentatives(10L, 100L, me)).thenReturn(List.of());

        RepresentativesView view = service.getRepresentatives(me, 10L, 100L);

        assertThat(view.cycleId()).isEqualTo(10L);
        verify(queryPort).findRepresentatives(10L, 100L, me);
        verify(cyclePort, never()).findCurrent(any());
    }

    @Test
    void 없는_cycleId면_404다() {
        when(cyclePort.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRepresentatives(me, 99L, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getReason()).isEqualTo("사이클을 찾을 수 없어요.");
                });
    }

    @Test
    void 진행_중인_사이클이_없으면_빈_결과다() {
        when(cyclePort.findCurrent(any())).thenReturn(Optional.empty());

        RepresentativesView view = service.getRepresentatives(me, null, null);

        assertThat(view.cycleId()).isNull();
        assertThat(view.representativesSelected()).isFalse();
        assertThat(view.representatives()).isEmpty();
    }

    private Representative representative(UUID userId, String name, long locationId, int rank, long score,
                                           long dongCode, AssignmentType type) {
        Users user = mock(Users.class);
        when(user.getUserId()).thenReturn(userId);
        when(user.getUserName()).thenReturn(name);
        Location location = mock(Location.class);
        when(location.getLocationId()).thenReturn(locationId);
        Dong dong = mock(Dong.class);
        when(dong.getDongId()).thenReturn(dongCode % 1000);
        when(dong.getDongCode()).thenReturn(dongCode);
        when(dong.getDongName()).thenReturn("동");
        return Representative.of(cycle, user, location, rank, score, dong, type);
    }

    private static Stage stage(long id, int round, LocalDateTime startedAt) {
        Stage stage = Stage.create(1, round, startedAt, startedAt.plusDays(7));
        ReflectionTestUtils.setField(stage, "stageId", id);
        return stage;
    }
}
