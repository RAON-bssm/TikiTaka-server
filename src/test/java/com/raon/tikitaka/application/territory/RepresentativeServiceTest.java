package com.raon.tikitaka.application.territory;

import com.raon.tikitaka.application.location.out.LocationRepositoryPort;
import com.raon.tikitaka.application.territory.out.DongPreferenceRepositoryPort;
import com.raon.tikitaka.application.territory.out.DongRepositoryPort;
import com.raon.tikitaka.application.territory.out.RandomPort;
import com.raon.tikitaka.application.territory.out.RepresentativeCandidatePort;
import com.raon.tikitaka.application.territory.out.RepresentativeCandidatePort.CandidateRow;
import com.raon.tikitaka.application.territory.out.TerritoryCycleRepositoryPort;
import com.raon.tikitaka.application.territory.out.TerritorySetupPort;
import com.raon.tikitaka.application.territory.policy.AssignmentPolicy;
import com.raon.tikitaka.domain.enums.AssignmentType;
import com.raon.tikitaka.domain.enums.TerritoryEventType;
import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.domain.match.Stage;
import com.raon.tikitaka.domain.territory.Dong;
import com.raon.tikitaka.domain.territory.DongPreference;
import com.raon.tikitaka.domain.territory.Representative;
import com.raon.tikitaka.domain.territory.Territory;
import com.raon.tikitaka.domain.territory.TerritoryCycle;
import com.raon.tikitaka.domain.territory.TerritoryHistory;
import com.raon.tikitaka.domain.user.Users;
import com.raon.tikitaka.global.config.TerritoryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 대표 선발 배치. 구별 후보 수, 희망 동 배치, 랜덤 배정, 주인 없는 땅을 본다.
 * 후보 쿼리(점수 0·비활성 제외, 동점 순서)와 재실행·롤백은 실제 DB가 필요해 RepresentativeSelectionDbTest에서 본다.
 */
class RepresentativeServiceTest {

    private static final long POINT_STAGE_ID = 1L;
    private static final TerritoryProperties PROPS = new TerritoryProperties(4, 5,
            new TerritoryProperties.LandPrice(2500), new TerritoryProperties.Defense(60));

    private final TerritoryCycleRepositoryPort cyclePort = mock(TerritoryCycleRepositoryPort.class);
    private final LocationRepositoryPort locationPort = mock(LocationRepositoryPort.class);
    private final DongRepositoryPort dongPort = mock(DongRepositoryPort.class);
    private final DongPreferenceRepositoryPort preferencePort = mock(DongPreferenceRepositoryPort.class);
    private final RepresentativeCandidatePort candidatePort = mock(RepresentativeCandidatePort.class);
    private final TerritorySetupPort setupPort = mock(TerritorySetupPort.class);
    /** 남은 동을 뒤집어서 섞는다. 랜덤 배정이 실제로 섞인 목록을 쓰는지 드러난다 */
    private final List<List<?>> shuffled = new ArrayList<>();
    private final RandomPort reverse = new RandomPort() {
        public <T> void shuffle(List<T> list) {
            shuffled.add(List.copyOf(list));
            Collections.reverse(list);
        }
    };
    private final RepresentativeService service = new RepresentativeService(cyclePort, locationPort, dongPort,
            preferencePort, candidatePort, setupPort, new AssignmentPolicy(reverse), PROPS);

    private TerritoryCycle cycle;
    private final Location haeundae = location(10L);
    private final Location suyeong = location(20L);
    private final Location outside = location(30L);
    private final Dong 우1 = dong(101L, haeundae), 우2 = dong(102L, haeundae), 우3 = dong(103L, haeundae);
    private final Dong 광안1 = dong(201L, suyeong), 광안2 = dong(202L, suyeong);
    private final Map<UUID, Users> users = new HashMap<>();

    @BeforeEach
    void setUp() {
        // 동결(4일차 종료) 6시간 뒤. 선발 대기 상태다
        LocalDateTime start = LocalDateTime.now().minusDays(4).minusHours(6);
        cycle = TerritoryCycle.create(stage(POINT_STAGE_ID, 1, start), stage(2L, 2, start.plusDays(7)), 4);
        when(cyclePort.findAllAwaitingSelection(any())).thenReturn(List.of(cycle));
        when(locationPort.findAll()).thenReturn(List.of(haeundae, suyeong, outside));
        when(dongPort.findAllByLocationId(10L)).thenReturn(List.of(우1, 우2, 우3));
        when(dongPort.findAllByLocationId(20L)).thenReturn(List.of(광안1, 광안2));
        when(dongPort.findAllByLocationId(30L)).thenReturn(List.of());
        lenient().when(candidatePort.findTopCandidates(anyLong(), anyLong(), anyInt())).thenReturn(List.of());
        lenient().when(setupPort.saveTerritories(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(setupPort.findUsersByIds(any())).thenAnswer(inv -> ((Collection<UUID>) inv.getArgument(0))
                .stream().map(users::get).toList());
        lenient().when(preferencePort.findAllByUserIds(any())).thenReturn(List.of());
    }

    @Test
    void 구마다_동_수만큼만_후보를_뽑고_동_데이터가_없는_구는_건너뛴다() {
        UUID a = user(), b = user(), c = user();
        candidates(10L, 3, a, b, c);

        service.execute();

        verify(candidatePort).findTopCandidates(POINT_STAGE_ID, 10L, 3);
        verify(candidatePort).findTopCandidates(POINT_STAGE_ID, 20L, 2);
        verify(candidatePort, never()).findTopCandidates(anyLong(), eq(30L), anyInt());
        assertThat(savedRepresentatives()).hasSize(3)
                .allSatisfy(r -> assertThat(r.getLocation()).isSameAs(haeundae));
        assertThat(savedTerritories()).hasSize(5);
    }

    @Test
    void 희망_동이_겹치면_순위가_높은_대표가_받는다() {
        UUID first = user(), second = user();
        candidates(10L, 3, first, second);
        preferences(Map.of(first, List.of(우2), second, List.of(우2, 우3)));

        service.execute();

        assertThat(savedRepresentatives())
                .extracting(r -> r.getUser().getUserId(), Representative::getRank, r -> r.getAssignedDong().getDongId(),
                        Representative::getAssignedBy)
                .containsExactly(
                        tuple(first, 1, 102L, AssignmentType.PREFERENCE_1),
                        tuple(second, 2, 103L, AssignmentType.PREFERENCE_2));
    }

    @Test
    void 희망_동을_못_받은_대표는_희망_배치가_끝난_뒤_남은_동에_랜덤으로_배정된다() {
        UUID first = user(), second = user();
        candidates(10L, 3, first, second);
        // 1위는 희망 동이 없다. 랜덤이 먼저 돌면 2위의 희망 동(우1)을 빼앗을 수 있다
        preferences(Map.of(second, List.of(우1)));

        service.execute();

        assertThat(savedRepresentatives())
                .extracting(r -> r.getUser().getUserId(), r -> r.getAssignedDong().getDongId(),
                        Representative::getAssignedBy)
                .containsExactly(
                        tuple(first, 103L, AssignmentType.RANDOM),        // 남은 [우2, 우3]을 섞은 첫 동
                        tuple(second, 101L, AssignmentType.PREFERENCE_1));
        assertThat(shuffled).containsExactly(List.of(102L, 103L));
    }

    @Test
    void 대표가_모자라면_남는_동은_주인_없는_땅으로_시작하고_이력은_배정된_땅만_남긴다() {
        UUID only = user();
        candidates(10L, 3, only);

        service.execute();

        List<Territory> haeundaeLands = savedTerritories().stream()
                .filter(t -> t.getDong().getLocation() == haeundae).toList();
        assertThat(haeundaeLands).hasSize(3);
        assertThat(haeundaeLands).filteredOn(Territory::isOwned).singleElement()
                .satisfies(t -> {
                    assertThat(t.getOwner().getUserId()).isEqualTo(only);
                    assertThat(t.getOwnerLocation()).isSameAs(haeundae);
                });
        assertThat(savedTerritories()).filteredOn(t -> !t.isOwned()).hasSize(4)
                .allSatisfy(t -> {
                    assertThat(t.getDefenseScore()).isEqualTo(60);
                    assertThat(t.landPrice()).isEqualTo(2500);
                });
        assertThat(savedHistories()).singleElement().satisfies(h -> {
            assertThat(h.getToOwnerId()).isEqualTo(only);
            assertThat(h.getReason()).isEqualTo(TerritoryEventType.ASSIGNED);
        });
    }

    @Test
    void 다른_구의_희망_동은_배치에서_무시한다() {
        UUID first = user();
        candidates(10L, 3, first);
        preferences(Map.of(first, List.of(광안1, 우3)));

        service.execute();

        assertThat(savedRepresentatives()).singleElement().satisfies(r -> {
            assertThat(r.getAssignedDong()).isSameAs(우3);
            assertThat(r.getAssignedBy()).isEqualTo(AssignmentType.PREFERENCE_1);
        });
    }

    @Test
    void 선발이_끝나면_사이클에_표시하고_대표_점수는_선발_시점_랭킹으로_남긴다() {
        UUID first = user();
        when(candidatePort.findTopCandidates(POINT_STAGE_ID, 10L, 3)).thenReturn(List.of(new CandidateRow(first, 777L)));

        service.execute();

        assertThat(cycle.isRepresentativesSelected()).isTrue();
        assertThat(savedRepresentatives()).singleElement().extracting(Representative::getScore).isEqualTo(777L);
    }

    @Test
    void 동결_전_글이_심사_중이어도_기다리지_않고_선발한다() {
        when(candidatePort.countPendingReviews(POINT_STAGE_ID, cycle.getRankingFreezeAt())).thenReturn(3L);
        UUID first = user();
        candidates(10L, 3, first);

        service.execute();

        verify(candidatePort).countPendingReviews(POINT_STAGE_ID, cycle.getRankingFreezeAt());
        assertThat(cycle.isRepresentativesSelected()).isTrue();
        assertThat(savedRepresentatives()).hasSize(1);
    }

    // ===== 헬퍼 =====

    private UUID user() {
        UUID id = UUID.randomUUID();
        Users user = mock(Users.class);
        lenient().when(user.getUserId()).thenReturn(id);
        users.put(id, user);
        return id;
    }

    /** 점수 내림차순으로 받은 순서가 곧 순위다 */
    private void candidates(long locationId, int limit, UUID... ranked) {
        List<CandidateRow> rows = new ArrayList<>();
        for (int i = 0; i < ranked.length; i++) {
            rows.add(new CandidateRow(ranked[i], 1000L - i));
        }
        when(candidatePort.findTopCandidates(POINT_STAGE_ID, locationId, limit)).thenReturn(rows);
    }

    private void preferences(Map<UUID, List<Dong>> byUser) {
        List<DongPreference> prefs = new ArrayList<>();
        byUser.forEach((userId, dongs) -> {
            for (Dong d : dongs) {
                DongPreference p = mock(DongPreference.class);
                when(p.getUser()).thenReturn(users.get(userId));
                when(p.getDong()).thenReturn(d);
                prefs.add(p);
            }
        });
        when(preferencePort.findAllByUserIds(any())).thenReturn(prefs);
    }

    @SuppressWarnings("unchecked")
    private List<Representative> savedRepresentatives() {
        ArgumentCaptor<List<Representative>> captor = ArgumentCaptor.forClass(List.class);
        verify(setupPort, atLeast(0)).saveRepresentatives(captor.capture());
        return captor.getAllValues().stream().flatMap(List::stream).toList();
    }

    @SuppressWarnings("unchecked")
    private List<Territory> savedTerritories() {
        ArgumentCaptor<List<Territory>> captor = ArgumentCaptor.forClass(List.class);
        verify(setupPort, times(2)).saveTerritories(captor.capture());   // 동 데이터가 있는 두 구
        return captor.getAllValues().stream().flatMap(List::stream).toList();
    }

    @SuppressWarnings("unchecked")
    private List<TerritoryHistory> savedHistories() {
        ArgumentCaptor<List<TerritoryHistory>> captor = ArgumentCaptor.forClass(List.class);
        verify(setupPort, times(2)).saveHistories(captor.capture());
        return captor.getAllValues().stream().flatMap(List::stream).toList();
    }

    private static Stage stage(long id, int round, LocalDateTime startedAt) {
        Stage stage = Stage.create(1, round, startedAt, startedAt.plusDays(7));
        ReflectionTestUtils.setField(stage, "stageId", id);
        return stage;
    }

    private static Location location(long id) {
        Location location = mock(Location.class);
        lenient().when(location.getLocationId()).thenReturn(id);
        return location;
    }

    private static Dong dong(long id, Location location) {
        Dong dong = mock(Dong.class);
        lenient().when(dong.getDongId()).thenReturn(id);
        lenient().when(dong.getLocation()).thenReturn(location);
        return dong;
    }
}
