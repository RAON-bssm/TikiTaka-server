package com.raon.tikitaka.adapter;

import com.raon.tikitaka.adapter.post.out.PostJpaRepository;
import com.raon.tikitaka.domain.board.Board;
import com.raon.tikitaka.domain.enums.LoginProvider;
import com.raon.tikitaka.domain.enums.MatchType;
import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.domain.match.Match;
import com.raon.tikitaka.domain.match.Stage;
import com.raon.tikitaka.domain.post.Post;
import com.raon.tikitaka.domain.post.PostStatus;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 게시물 심사 상태와 대기열 쿼리를 실제 PostgreSQL에서 돌려 본다.
 * WithdrawnUserQueryTest와 같이 TEST_DATABASE_URL이 없으면 건너뛴다.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class PostReviewQueryTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("TEST_DATABASE_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("TEST_DATABASE_PASSWORD"));
    }

    @Autowired EntityManager em;
    @Autowired PostJpaRepository postJpaRepository;

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

    // ===== 피드·상세 =====

    @Test
    void 피드에는_DONE_글만_나온다() {
        Users author = user("author");
        Users viewer = user("viewer");
        Post done = post(author, PostStatus.DONE);
        for (PostStatus status : PostStatus.values()) {
            if (status != PostStatus.DONE) {
                post(author, status);
            }
        }
        flushAndClear();

        assertThat(postJpaRepository.findAllActiveByBoardId(board.getBoardId()))
                .extracting(Post::getPostId).containsExactly(done.getPostId());
        assertThat(postJpaRepository.findAllActiveByBoardIdExcludingBlockedBy(board.getBoardId(), viewer.getUserId()))
                .extracting(Post::getPostId).containsExactly(done.getPostId());
    }

    @Test
    void 피드의_탈퇴_작성자_제외는_상태_조건과_함께_유지된다() {
        Users withdrawn = user("withdrawn");
        Users viewer = user("viewer");
        post(withdrawn, PostStatus.DONE);
        flushAndClear();
        em.find(Users.class, withdrawn.getUserId()).withdraw();
        flushAndClear();

        assertThat(postJpaRepository.findAllActiveByBoardId(board.getBoardId())).isEmpty();
        assertThat(postJpaRepository.findAllActiveByBoardIdExcludingBlockedBy(board.getBoardId(), viewer.getUserId()))
                .isEmpty();
    }

    @Test
    void 상세용_조회는_상태를_거르지_않는다() {
        Users author = user("author");
        Post pending = post(author, PostStatus.PENDING);
        flushAndClear();

        // 상태 규칙(본인·관리자만)은 서비스가 판단하므로 쿼리는 PENDING도 돌려줘야 한다
        assertThat(postJpaRepository.findActiveById(pending.getPostId()))
                .hasValueSatisfying(p -> assertThat(p.getStatus()).isEqualTo(PostStatus.PENDING));
    }

    @Test
    void 내_게시물에는_모든_상태가_나온다() {
        Users author = user("author");
        for (PostStatus status : PostStatus.values()) {
            post(author, status);
        }
        flushAndClear();

        assertThat(postJpaRepository.findAllActiveByUserId(author.getUserId()))
                .extracting(Post::getStatus).containsExactlyInAnyOrder(PostStatus.values());
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

    private Post post(Users author, PostStatus status) {
        Post post = Post.create(author, board, "내용", "key", status == PostStatus.DONE ? 80 : null, null,
                home.getLocationName(), home, true);
        ReflectionTestUtils.setField(post, "status", status);
        em.persist(post);
        return post;
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }
}
