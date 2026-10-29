package com.raon.tikitaka.adapter;

import com.raon.tikitaka.adapter.post.out.PostJpaRepository;
import com.raon.tikitaka.adapter.ranking.out.LocationRankingJpaRepository;
import com.raon.tikitaka.adapter.ranking.out.UserRankingJpaRepository;
import com.raon.tikitaka.application.ranking.UserRankRow;
import com.raon.tikitaka.domain.block.UserBlock;
import com.raon.tikitaka.domain.board.Board;
import com.raon.tikitaka.domain.enums.LoginProvider;
import com.raon.tikitaka.domain.enums.MatchType;
import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.domain.match.Match;
import com.raon.tikitaka.domain.match.Stage;
import com.raon.tikitaka.domain.post.Post;
import com.raon.tikitaka.domain.user.Users;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 탈퇴한 유저를 조회에서 거르는 쿼리를 실제 PostgreSQL에서 돌려 본다.
 * 네이티브 쿼리(rank(), cast)는 Mockito로 검증할 수 없고 H2와도 동작이 달라 PostgreSQL이 필요하다.
 * TEST_DATABASE_URL이 없으면 건너뛴다. 예:
 * TEST_DATABASE_URL=jdbc:postgresql://localhost:5432/tikitaka_test TEST_DATABASE_USERNAME=... TEST_DATABASE_PASSWORD=... ./gradlew test
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class WithdrawnUserQueryTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("TEST_DATABASE_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("TEST_DATABASE_PASSWORD"));
    }

    @Autowired EntityManager em;
    @Autowired PostJpaRepository postJpaRepository;
    @Autowired UserRankingJpaRepository userRankingJpaRepository;
    @Autowired LocationRankingJpaRepository locationRankingJpaRepository;

    private Location home;
    private Stage stage;
    private Board board;

    @BeforeEach
    void setUp() {
        home = location("해운대구");
        Location away = location("수영구");
        stage = Stage.create(1, 1, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(6));
        em.persist(stage);
        Match match = Match.create(stage, home, away, MatchType.NORMAL, 1, 1, "미션");
        em.persist(match);
        board = Board.create(match);
        em.persist(board);
    }

    // ===== 게시물 =====

    @Test
    void 탈퇴한_작성자의_글은_비로그인_목록에서_빠진다() {
        Users active = user("active");
        Users withdrawn = user("withdrawn");
        Post activePost = post(active);
        post(withdrawn);
        withdraw(withdrawn);

        List<Post> posts = postJpaRepository.findAllActiveByBoardId(board.getBoardId());

        assertThat(posts).extracting(Post::getPostId).containsExactly(activePost.getPostId());
    }

    @Test
    void 탈퇴한_작성자의_글은_로그인_목록에서_빠지고_차단_필터도_그대로_동작한다() {
        Users viewer = user("viewer");
        Users active = user("active");
        Users blocked = user("blocked");
        Users withdrawn = user("withdrawn");
        Post activePost = post(active);
        post(blocked);
        post(withdrawn);
        em.persist(UserBlock.of(viewer, blocked));
        withdraw(withdrawn);

        List<Post> posts = postJpaRepository.findAllActiveByBoardIdExcludingBlockedBy(board.getBoardId(),
                viewer.getUserId());

        assertThat(posts).extracting(Post::getPostId).containsExactly(activePost.getPostId());
    }

    @Test
    void 탈퇴한_작성자의_글은_상세_조회에서_빈_결과가_된다() {
        Users active = user("active");
        Users withdrawn = user("withdrawn");
        Post activePost = post(active);
        Post withdrawnPost = post(withdrawn);
        withdraw(withdrawn);

        assertThat(postJpaRepository.findActiveById(withdrawnPost.getPostId())).isEmpty();
        assertThat(postJpaRepository.findActiveById(activePost.getPostId())).isPresent();
        // 숨기기만 하고 데이터는 남는다
        assertThat(postJpaRepository.findById(withdrawnPost.getPostId()))
                .hasValueSatisfying(p -> assertThat(p.isActive()).isTrue());
    }

    // ===== 개인 랭킹 =====

    @Test
    void 일등이_탈퇴하면_남은_유저의_순위가_구멍_없이_당겨진다() {
        Users first = ranked("first", 100);
        Users second = ranked("second", 90);
        Users third = ranked("third", 80);
        withdraw(first);

        List<UserRankRow> rows = userRankingJpaRepository.findTopRanking(stage.getStageId(), 10);

        assertThat(rows).extracting(UserRankRow::getUserId, UserRankRow::getUserRank)
                .containsExactly(tuple(second.getUserId(), 1), tuple(third.getUserId(), 2));
    }

    @Test
    void 차단_필터와_같이_쓰면_탈퇴는_순위에서_빠지고_차단은_원래_순위를_유지한다() {
        Users viewer = user("viewer");
        Users first = ranked("first", 100);
        Users second = ranked("second", 90);
        Users third = ranked("third", 80);
        Users fourth = ranked("fourth", 70);
        em.persist(UserBlock.of(viewer, third));
        withdraw(first);

        List<UserRankRow> rows = userRankingJpaRepository.findTopRankingExcludingBlockedBy(stage.getStageId(),
                viewer.getUserId(), 10);

        // 탈퇴한 1등이 빠져 2등이 1위가 되고, 차단한 유저(2위)는 자리를 비운 채 4등은 3위로 남는다
        assertThat(rows).extracting(UserRankRow::getUserId, UserRankRow::getUserRank)
                .containsExactly(tuple(second.getUserId(), 1), tuple(fourth.getUserId(), 3));
    }

    @Test
    void 일등이_탈퇴하면_내_순위도_목록과_같게_당겨진다() {
        Users first = ranked("first", 100);
        Users second = ranked("second", 90);
        Users third = ranked("third", 80);
        withdraw(first);

        assertThat(userRankingJpaRepository.findMyRanking(stage.getStageId(), second.getUserId()))
                .hasValueSatisfying(row -> assertThat(row.getUserRank()).isEqualTo(1));
        assertThat(userRankingJpaRepository.findMyRanking(stage.getStageId(), third.getUserId()))
                .hasValueSatisfying(row -> assertThat(row.getUserRank()).isEqualTo(2));
        // 탈퇴한 유저 본인의 행은 순위 대상이 아니다
        assertThat(userRankingJpaRepository.findMyRanking(stage.getStageId(), first.getUserId())).isEmpty();
    }

    // ===== 점수 유지 =====

    @Test
    void 탈퇴해도_개인_점수_행과_지역_점수는_그대로_남는다() {
        Users withdrawn = ranked("withdrawn", 100);
        locationRankingJpaRepository.upsertScore(stage.getStageId(), home.getLocationId(), 300,
                LocalDateTime.now());
        withdraw(withdrawn);

        assertThat(userScore(withdrawn)).isEqualTo(100L);
        assertThat(locationScore(home)).isEqualTo(300L);
    }

    // ===== helpers =====

    private Location location(String name) {
        em.createNativeQuery("insert into location (city_name, location_name) values ('부산광역시', :name)")
                .setParameter("name", name)
                .executeUpdate();
        Long id = ((Number) em.createNativeQuery("select location_id from location where location_name = :name")
                .setParameter("name", name)
                .getSingleResult()).longValue();
        return em.find(Location.class, id);
    }

    private Users user(String name) {
        Users user = Users.of(name, LoginProvider.KAKAO, name + "-provider", home);
        em.persist(user);
        return user;
    }

    private Users ranked(String name, long score) {
        Users user = user(name);
        em.flush();
        userRankingJpaRepository.upsertScore(stage.getStageId(), user.getUserId(), score, LocalDateTime.now());
        return user;
    }

    private Post post(Users author) {
        Post post = Post.create(author, board, "내용", null, 80, null, home.getLocationName(), home, true);
        em.persist(post);
        return post;
    }

    /**
     * 실제 탈퇴와 같은 Users.withdraw()만 부르고, 쿼리가 DB 값을 읽도록 반영한 뒤 영속성 컨텍스트를 비운다.
     */
    private void withdraw(Users user) {
        Users managed = em.find(Users.class, user.getUserId());
        managed.withdraw();
        em.flush();
        em.clear();
    }

    private Long userScore(Users user) {
        return ((Number) em.createNativeQuery(
                        "select user_score from user_ranking where stage_id = :stageId and user_id = :userId")
                .setParameter("stageId", stage.getStageId())
                .setParameter("userId", user.getUserId())
                .getSingleResult()).longValue();
    }

    private Long locationScore(Location location) {
        return ((Number) em.createNativeQuery(
                        "select location_score from location_ranking where stage_id = :stageId and location_id = :locationId")
                .setParameter("stageId", stage.getStageId())
                .setParameter("locationId", location.getLocationId())
                .getSingleResult()).longValue();
    }
}
