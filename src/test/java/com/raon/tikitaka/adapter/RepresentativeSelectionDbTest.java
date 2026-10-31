package com.raon.tikitaka.adapter;

import com.raon.tikitaka.adapter.location.out.LocationPersistenceAdapter;
import com.raon.tikitaka.adapter.ranking.out.UserRankingJpaRepository;
import com.raon.tikitaka.adapter.territory.out.DongPersistenceAdapter;
import com.raon.tikitaka.adapter.territory.out.DongPreferencePersistenceAdapter;
import com.raon.tikitaka.adapter.territory.out.TerritoryCyclePersistenceAdapter;
import com.raon.tikitaka.adapter.territory.out.TerritorySetupPersistenceAdapter;
import com.raon.tikitaka.application.territory.RepresentativeService;
import com.raon.tikitaka.application.territory.out.RandomPort;
import com.raon.tikitaka.application.territory.out.RepresentativeCandidatePort;
import com.raon.tikitaka.application.territory.out.RepresentativeCandidatePort.CandidateRow;
import com.raon.tikitaka.application.territory.policy.AssignmentPolicy;
import com.raon.tikitaka.domain.board.Board;
import com.raon.tikitaka.domain.enums.LoginProvider;
import com.raon.tikitaka.domain.enums.MatchType;
import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.domain.match.Match;
import com.raon.tikitaka.domain.match.Stage;
import com.raon.tikitaka.domain.post.Post;
import com.raon.tikitaka.domain.territory.TerritoryCycle;
import com.raon.tikitaka.domain.user.Users;
import com.raon.tikitaka.global.config.TerritoryProperties;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 대표 선발을 실제 PostgreSQL에서 돌려 본다. 후보 쿼리(네이티브), 재실행 멱등, 중간 실패 시 롤백을 본다.
 * 서비스가 자기 트랜잭션을 커밋·롤백해야 하므로 테스트 트랜잭션을 끄고, 매 테스트 뒤에 테이블을 비운다.
 * TEST_DATABASE_URL이 없으면 건너뛴다. 예:
 * TEST_DATABASE_URL=jdbc:postgresql://localhost:5432/tikitaka_test TEST_DATABASE_USERNAME=... TEST_DATABASE_PASSWORD=... ./gradlew test
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({TerritoryCyclePersistenceAdapter.class, LocationPersistenceAdapter.class, DongPersistenceAdapter.class,
        DongPreferencePersistenceAdapter.class, TerritorySetupPersistenceAdapter.class, AssignmentPolicy.class,
        RepresentativeService.class, RepresentativeSelectionDbTest.Config.class})
class RepresentativeSelectionDbTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("TEST_DATABASE_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("TEST_DATABASE_PASSWORD"));
    }

    /** failFromCall번째 shuffle부터 예외를 던진다. 0이면 던지지 않는다 */
    static class FailingRandom implements RandomPort {
        final AtomicInteger calls = new AtomicInteger();
        volatile int failFromCall;

        @Override
        public <T> void shuffle(List<T> list) {
            if (failFromCall > 0 && calls.incrementAndGet() >= failFromCall) {
                throw new IllegalStateException("테스트용 실패");
            }
            Collections.reverse(list);
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        TerritoryProperties territoryProperties() {
            return new TerritoryProperties(4, 5, new TerritoryProperties.LandPrice(2500),
                    new TerritoryProperties.Defense(60));
        }

        @Bean
        FailingRandom randomPort() {
            return new FailingRandom();
        }
    }

    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager txManager;
    @Autowired UserRankingJpaRepository userRankingJpaRepository;
    @Autowired RepresentativeCandidatePort candidatePort;
    @Autowired RepresentativeService representativeService;
    @Autowired FailingRandom random;

    private TransactionTemplate tx;
    private Location haeundae;
    private Location suyeong;
    private Stage point;
    private Stage otherRound;
    private Long cycleId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
        random.failFromCall = 0;
        random.calls.set(0);
        tx.executeWithoutResult(s -> {
            haeundae = location("해운대구");
            suyeong = location("수영구");
            dong(2635051000L, haeundae, "우1동");
            dong(2635052000L, haeundae, "우2동");
            dong(2635053000L, haeundae, "우3동");
            dong(2650051000L, suyeong, "광안1동");
            // 동결(4일차 종료) 6시간 뒤
            LocalDateTime start = LocalDateTime.now().minusDays(4).minusHours(6);
            point = Stage.create(1, 1, start, start.plusDays(7));
            em.persist(point);
            Stage battle = Stage.create(1, 2, start.plusDays(7), start.plusDays(14));
            em.persist(battle);
            otherRound = Stage.create(1, 3, start.plusDays(14), start.plusDays(21));
            em.persist(otherRound);
            TerritoryCycle cycle = TerritoryCycle.create(point, battle, 4);
            em.persist(cycle);
            cycleId = cycle.getCycleId();
        });
    }

    @AfterEach
    void tearDown() {
        tx.executeWithoutResult(s -> em.createNativeQuery("""
                truncate territory_history, representative, territory, dong_preference, territory_cycle, dong,
                         user_ranking, post, board, match, users, stage, location restart identity cascade
                """).executeUpdate());
    }

    // ===== 후보 쿼리 =====

    @Test
    void 후보는_본진이_그_구인_ACTIVE_유저_중_점수가_0보다_큰_사람이고_동점이면_먼저_도달한_사람이_앞선다() {
        LocalDateTime t = LocalDateTime.now().minusDays(1);
        UUID top = ranked("top", haeundae, 500, t);
        UUID tieLate = ranked("tieLate", haeundae, 300, t.plusMinutes(5));
        UUID tieEarly = ranked("tieEarly", haeundae, 300, t);
        UUID zero = ranked("zero", haeundae, 0, t);
        UUID minus = ranked("minus", haeundae, -10, t);
        UUID withdrawn = ranked("withdrawn", haeundae, 900, t);
        UUID dormant = ranked("dormant", haeundae, 800, t);
        UUID otherGu = ranked("otherGu", suyeong, 1000, t);
        UUID otherRoundOnly = user("otherRoundOnly", haeundae);
        score(otherRound, otherRoundOnly, 5000, t);
        score(otherRound, zero, 5000, t);           // 다른 라운드 점수는 섞이지 않는다
        tx.executeWithoutResult(s -> {
            em.find(Users.class, withdrawn).withdraw();
            em.find(Users.class, dormant).markDormant();
        });

        List<CandidateRow> rows = candidatePort.findTopCandidates(point.getStageId(), haeundae.getLocationId(), 10);

        assertThat(rows).extracting(CandidateRow::userId).containsExactly(top, tieEarly, tieLate);
        assertThat(rows).extracting(CandidateRow::score).containsExactly(500L, 300L, 300L);
        assertThat(rows).extracting(CandidateRow::userId).doesNotContain(zero, minus, withdrawn, dormant, otherGu,
                otherRoundOnly);
        assertThat(candidatePort.findTopCandidates(point.getStageId(), haeundae.getLocationId(), 2))
                .extracting(CandidateRow::userId).containsExactly(top, tieEarly);
    }

    @Test
    void 심사_대기_글은_동결_전에_올라온_그_라운드의_활성_글만_센다() {
        UUID author = user("author", haeundae);
        LocalDateTime freezeAt = point.getStartedAt().plusDays(4);
        tx.executeWithoutResult(s -> {
            Users u = em.find(Users.class, author);
            Location home = em.find(Location.class, haeundae.getLocationId());
            Match match = Match.create(em.find(Stage.class, point.getStageId()), home, home, MatchType.NORMAL, 1, 1, "미션");
            em.persist(match);
            Board board = Board.create(match);
            em.persist(board);
            pending(u, board, home, freezeAt.minusMinutes(1), true);    // 센다
            pending(u, board, home, freezeAt.minusHours(2), true);      // 센다
            pending(u, board, home, freezeAt.minusHours(1), false);     // 삭제된 글
            pending(u, board, home, freezeAt.plusMinutes(1), true);     // 동결 뒤에 올라온 글
            Post done = Post.create(u, board, "내용", null, 80, null, "해운대구", home, true);
            em.persist(done);
        });

        assertThat(candidatePort.countPendingReviews(point.getStageId(), freezeAt)).isEqualTo(2);
        assertThat(candidatePort.countPendingReviews(otherRound.getStageId(), freezeAt)).isZero();
    }

    // ===== 선발 배치 =====

    @Test
    void 두_번_돌려도_대표와_땅과_이력이_중복되지_않는다() {
        LocalDateTime t = LocalDateTime.now().minusDays(1);
        ranked("a", haeundae, 500, t);
        ranked("b", haeundae, 400, t);
        ranked("c", suyeong, 300, t);

        representativeService.execute();
        representativeService.execute();

        assertThat(count("representative")).isEqualTo(3);
        assertThat(count("territory")).isEqualTo(4);                  // 해운대 3 + 수영 1
        assertThat(count("territory where owner_id is null")).isEqualTo(1);   // 해운대 대표 2명, 동 3개
        assertThat(count("territory_history")).isEqualTo(3);
        assertThat(selected()).isTrue();
    }

    @Test
    void 중간에_실패하면_그_사이클은_통째로_롤백되고_다음_실행에서_다시_선발한다() {
        LocalDateTime t = LocalDateTime.now().minusDays(1);
        ranked("a", haeundae, 500, t);
        ranked("c", suyeong, 300, t);
        // 희망 동이 없어 두 구 모두 랜덤 배정(shuffle)을 탄다. 첫 구는 저장까지 끝나고 두 번째 구에서 실패한다
        random.failFromCall = 2;

        assertThatThrownBy(() -> representativeService.execute()).isInstanceOf(IllegalStateException.class);

        assertThat(count("representative")).isZero();
        assertThat(count("territory")).isZero();
        assertThat(count("territory_history")).isZero();
        assertThat(selected()).isFalse();

        random.failFromCall = 0;
        representativeService.execute();

        assertThat(count("representative")).isEqualTo(2);
        assertThat(count("territory")).isEqualTo(4);
        assertThat(selected()).isTrue();
    }

    // ===== 헬퍼 =====

    private Location location(String name) {
        em.createNativeQuery("insert into location (city_name, location_name) values ('부산광역시', :name)")
                .setParameter("name", name)
                .executeUpdate();
        Long id = ((Number) em.createNativeQuery("select location_id from location where location_name = :name")
                .setParameter("name", name)
                .getSingleResult()).longValue();
        return em.find(Location.class, id);
    }

    private void dong(long code, Location location, String name) {
        em.createNativeQuery("""
                        insert into dong (dong_code, location_id, dong_name, source_version)
                        values (:code, :locationId, :name, 'test')
                        """)
                .setParameter("code", code)
                .setParameter("locationId", location.getLocationId())
                .setParameter("name", name)
                .executeUpdate();
    }

    private UUID user(String name, Location home) {
        return tx.execute(s -> {
            Users user = Users.of(name, LoginProvider.KAKAO, name + "-provider",
                    em.find(Location.class, home.getLocationId()));
            em.persist(user);
            return user.getUserId();
        });
    }

    private UUID ranked(String name, Location home, long score, LocalDateTime at) {
        UUID id = user(name, home);
        score(point, id, score, at);
        return id;
    }

    private void score(Stage stage, UUID userId, long delta, LocalDateTime at) {
        tx.executeWithoutResult(s -> userRankingJpaRepository.upsertScore(stage.getStageId(), userId, delta, at));
    }

    private void pending(Users author, Board board, Location home, LocalDateTime createdAt, boolean active) {
        Post post = Post.createPending(author, board, "내용", "photo.jpg", "해운대구", home, true);
        em.persist(post);
        em.flush();
        em.createNativeQuery("update post set created_at = :at, is_active = :active where post_id = :id")
                .setParameter("at", createdAt)
                .setParameter("active", active)
                .setParameter("id", post.getPostId())
                .executeUpdate();
    }

    private long count(String tableAndCondition) {
        return ((Number) em.createNativeQuery("select count(*) from " + tableAndCondition).getSingleResult()).longValue();
    }

    private boolean selected() {
        return (Boolean) em.createNativeQuery("select representatives_selected from territory_cycle where cycle_id = :id")
                .setParameter("id", cycleId)
                .getSingleResult();
    }
}
