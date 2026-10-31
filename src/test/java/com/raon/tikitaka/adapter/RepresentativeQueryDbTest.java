package com.raon.tikitaka.adapter;

import com.raon.tikitaka.adapter.territory.out.TerritoryCyclePersistenceAdapter;
import com.raon.tikitaka.adapter.territory.out.TerritoryQueryPersistenceAdapter;
import com.raon.tikitaka.application.territory.TerritoryQueryService;
import com.raon.tikitaka.application.territory.view.RepresentativesView;
import com.raon.tikitaka.application.user.out.UserRepositoryPort;
import com.raon.tikitaka.domain.block.UserBlock;
import com.raon.tikitaka.domain.enums.AssignmentType;
import com.raon.tikitaka.domain.enums.LoginProvider;
import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.domain.match.Stage;
import com.raon.tikitaka.domain.territory.Dong;
import com.raon.tikitaka.domain.territory.Representative;
import com.raon.tikitaka.domain.territory.TerritoryCycle;
import com.raon.tikitaka.domain.user.Users;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;

/**
 * 선발 결과 조회(임시 API)를 실제 PostgreSQL에서 돌려 본다. 탈퇴·차단 유저 제외, 정렬, 사이클 분리, N+1 여부를 본다.
 * 서비스의 읽기 전용 트랜잭션에서 실행된 SQL 수를 세야 하므로 테스트 트랜잭션을 끄고, 매 테스트 뒤에 테이블을 비운다.
 * TEST_DATABASE_URL이 없으면 건너뛴다. 예:
 * TEST_DATABASE_URL=jdbc:postgresql://localhost:5432/tikitaka_test TEST_DATABASE_USERNAME=... TEST_DATABASE_PASSWORD=... ./gradlew test
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.generate_statistics=true"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({TerritoryQueryService.class, TerritoryCyclePersistenceAdapter.class, TerritoryQueryPersistenceAdapter.class,
        RepresentativeQueryDbTest.Config.class})
class RepresentativeQueryDbTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("TEST_DATABASE_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("TEST_DATABASE_PASSWORD"));
    }

    @TestConfiguration
    static class Config {
        /** 선발 결과 조회는 유저 포트를 쓰지 않는다. 같은 서비스의 다른 조회가 쓰므로 자리만 채운다 */
        @Bean
        UserRepositoryPort userRepositoryPort() {
            return mock(UserRepositoryPort.class);
        }
    }

    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager txManager;
    @Autowired TerritoryQueryService queryService;

    private TransactionTemplate tx;
    private Long haeundaeId;
    private Long suyeongId;
    private Long cycleA;
    private Long cycleB;
    private UUID me;
    private UUID alice;
    private UUID withdrawn;
    private UUID blocked;
    private UUID dave;
    private UUID erin;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
        tx.executeWithoutResult(s -> {
            Location haeundae = location("해운대구");
            Location suyeong = location("수영구");
            haeundaeId = haeundae.getLocationId();
            suyeongId = suyeong.getLocationId();
            Dong 우1 = dong(2635051000L, haeundae, "우1동");
            Dong 우2 = dong(2635052000L, haeundae, "우2동");
            Dong 우3 = dong(2635053000L, haeundae, "우3동");
            Dong 우4 = dong(2635054000L, haeundae, "우4동");
            Dong 광안1 = dong(2650051000L, suyeong, "광안1동");

            LocalDateTime start = LocalDateTime.now().minusDays(20);
            TerritoryCycle a = cycle(start, 1);
            TerritoryCycle b = cycle(start.plusDays(14), 3);
            cycleA = a.getCycleId();
            cycleB = b.getCycleId();

            Users meUser = user("me", haeundae);
            Users aliceUser = user("alice", haeundae);
            Users withdrawnUser = user("withdrawn", haeundae);
            Users blockedUser = user("blocked", haeundae);
            Users daveUser = user("dave", suyeong);
            Users erinUser = user("erin", haeundae);
            me = meUser.getUserId();
            alice = aliceUser.getUserId();
            withdrawn = withdrawnUser.getUserId();
            blocked = blockedUser.getUserId();
            dave = daveUser.getUserId();
            erin = erinUser.getUserId();

            // 정렬을 확인하려고 구·순위가 뒤섞인 순서로 저장한다
            em.persist(Representative.of(a, daveUser, suyeong, 1, 600, 광안1, AssignmentType.RANDOM));
            em.persist(Representative.of(a, meUser, haeundae, 4, 500, 우4, AssignmentType.RANDOM));
            em.persist(Representative.of(a, blockedUser, haeundae, 3, 700, 우3, AssignmentType.PREFERENCE_2));
            em.persist(Representative.of(a, aliceUser, haeundae, 1, 900, 우1, AssignmentType.PREFERENCE_1));
            em.persist(Representative.of(a, withdrawnUser, haeundae, 2, 800, 우2, AssignmentType.PREFERENCE_1));
            em.persist(Representative.of(b, erinUser, haeundae, 1, 100, 우1, AssignmentType.PREFERENCE_1));
            a.markRepresentativesSelected();
            b.markRepresentativesSelected();

            em.persist(UserBlock.of(meUser, blockedUser));
            withdrawnUser.withdraw();
        });
    }

    @AfterEach
    void tearDown() {
        tx.executeWithoutResult(s -> em.createNativeQuery("""
                truncate representative, territory_cycle, dong, user_block, users, stage, location
                restart identity cascade
                """).executeUpdate());
    }

    @Test
    void 탈퇴_유저와_내가_차단한_유저는_빠지고_순위는_선발_시점_값을_그대로_둔다() {
        RepresentativesView view = queryService.getRepresentatives(me, cycleA, null);

        assertThat(view.representativesSelected()).isTrue();
        assertThat(view.representatives())
                .extracting(RepresentativesView.Item::locationId, RepresentativesView.Item::rank,
                        RepresentativesView.Item::userId, RepresentativesView.Item::dongCode,
                        RepresentativesView.Item::mine)
                .containsExactly(
                        tuple(haeundaeId, 1, alice, 2635051000L, false),
                        tuple(haeundaeId, 4, me, 2635054000L, true),
                        tuple(suyeongId, 1, dave, 2650051000L, false));
    }

    @Test
    void 차단은_본인_기준이라_다른_유저에게는_보인다() {
        RepresentativesView view = queryService.getRepresentatives(alice, cycleA, haeundaeId);

        assertThat(view.representatives()).extracting(RepresentativesView.Item::userId)
                .containsExactly(alice, blocked, me);
        assertThat(view.representatives()).extracting(RepresentativesView.Item::userId).doesNotContain(withdrawn);
    }

    @Test
    void 구를_지정하면_그_구만_다른_사이클_결과는_섞이지_않는다() {
        assertThat(queryService.getRepresentatives(me, cycleA, suyeongId).representatives())
                .extracting(RepresentativesView.Item::userId).containsExactly(dave);
        assertThat(queryService.getRepresentatives(me, cycleB, null).representatives())
                .extracting(RepresentativesView.Item::userId).containsExactly(erin);
    }

    @Test
    void 유저와_동을_한_번에_읽어_대표_수와_관계없이_SQL은_사이클_조회와_대표_조회_두_번이다() {
        Statistics stats = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        stats.clear();

        RepresentativesView view = queryService.getRepresentatives(alice, cycleA, null);

        assertThat(view.representatives()).hasSize(4);
        assertThat(stats.getPrepareStatementCount()).isEqualTo(2);
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

    private Dong dong(long code, Location location, String name) {
        em.createNativeQuery("""
                        insert into dong (dong_code, location_id, dong_name, source_version)
                        values (:code, :locationId, :name, 'test')
                        """)
                .setParameter("code", code)
                .setParameter("locationId", location.getLocationId())
                .setParameter("name", name)
                .executeUpdate();
        Long id = ((Number) em.createNativeQuery("select dong_id from dong where dong_code = :code")
                .setParameter("code", code)
                .getSingleResult()).longValue();
        return em.find(Dong.class, id);
    }

    private TerritoryCycle cycle(LocalDateTime start, int pointRound) {
        Stage point = Stage.create(1, pointRound, start, start.plusDays(7));
        Stage battle = Stage.create(1, pointRound + 1, start.plusDays(7), start.plusDays(14));
        em.persist(point);
        em.persist(battle);
        TerritoryCycle cycle = TerritoryCycle.create(point, battle, 4);
        em.persist(cycle);
        return cycle;
    }

    private Users user(String name, Location home) {
        Users user = Users.of(name, LoginProvider.KAKAO, name + "-provider", home);
        em.persist(user);
        return user;
    }
}
