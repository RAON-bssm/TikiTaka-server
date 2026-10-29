package com.raon.tikitaka.adapter;

import com.raon.tikitaka.adapter.post.out.PostJpaRepository;
import com.raon.tikitaka.application.post.PostUploadLimitService;
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
import org.springframework.data.domain.Limit;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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

    // ===== 대기열 선점 =====

    @Test
    void 선점은_백오프_중이거나_최근에_선점된_글을_건너뛰고_오래_멈춘_글은_다시_가져간다() {
        Users author = user("author");
        LocalDateTime now = LocalDateTime.now();
        Post fresh = post(author, PostStatus.PENDING);
        Post backoff = post(author, PostStatus.PENDING);
        Post claimedRecently = post(author, PostStatus.PENDING);
        Post stale = post(author, PostStatus.PENDING);
        Post inactive = post(author, PostStatus.PENDING);
        post(author, PostStatus.FAILED);
        flushAndClear();
        setColumn(fresh, "created_at", now.minusMinutes(10));
        setColumn(stale, "created_at", now.minusMinutes(9));
        setColumn(backoff, "review_next_at", now.plusMinutes(1));
        setColumn(claimedRecently, "review_started_at", now.minusMinutes(1));
        setColumn(stale, "review_started_at", now.minusMinutes(6));
        em.createNativeQuery("update post set is_active = false where post_id = :id")
                .setParameter("id", inactive.getPostId()).executeUpdate();

        List<UUID> ids = postJpaRepository.lockClaimableIds(now, now.minusMinutes(5), 10);

        // 오래된 순. 서버가 처리 도중 죽어 6분째 멈춘 글도 다시 가져간다
        assertThat(ids).containsExactly(fresh.getPostId(), stale.getPostId());
        // 워커가 선점 전에 세는 수도 같은 조건이라 백오프 중이거나 심사 중인 글은 세지 않는다
        assertThat(postJpaRepository.countClaimable(now, now.minusMinutes(5))).isEqualTo(ids.size());
    }

    @Test
    void 선점은_limit만큼만_가져가고_선점_시각만_기록하며_시도_횟수는_그대로다() {
        Users author = user("author");
        Post first = post(author, PostStatus.PENDING);
        Post second = post(author, PostStatus.PENDING);
        flushAndClear();
        LocalDateTime now = LocalDateTime.now();
        setColumn(first, "created_at", now.minusMinutes(2));
        setColumn(second, "created_at", now.minusMinutes(1));

        List<UUID> ids = postJpaRepository.lockClaimableIds(now, now.minusMinutes(5), 1);
        postJpaRepository.markClaimed(ids, now);

        assertThat(ids).containsExactly(first.getPostId());
        Post claimed = postJpaRepository.findById(first.getPostId()).orElseThrow();
        assertThat(claimed.getReviewStartedAt()).isNotNull();
        assertThat(claimed.getReviewAttempts()).isZero();
        // 다시 선점하려 해도 방금 선점한 글은 빠진다
        assertThat(postJpaRepository.lockClaimableIds(now, now.minusMinutes(5), 10))
                .containsExactly(second.getPostId());
    }

    @Test
    void 잠금_조회는_탈퇴한_작성자의_글과_삭제된_글도_돌려준다() {
        Users withdrawn = user("withdrawn");
        Post post = post(withdrawn, PostStatus.PENDING);
        flushAndClear();
        em.find(Users.class, withdrawn.getUserId()).withdraw();
        flushAndClear();

        // findActiveById는 탈퇴 작성자의 글을 숨기지만, 심사 완료는 이 조회로 읽으므로 글을 놓치지 않는다
        assertThat(postJpaRepository.findActiveById(post.getPostId())).isEmpty();
        assertThat(postJpaRepository.findByIdForUpdate(post.getPostId())).isPresent();
    }

    // ===== 업로드 한도 =====

    @Test
    void 업로드_횟수는_기간_안의_PENDING_DONE_REJECTED와_삭제한_글을_최근_순으로_센다() {
        Users author = user("author");
        Users other = user("other");
        LocalDateTime now = LocalDateTime.now();
        Post done = post(author, PostStatus.DONE);
        Post pending = post(author, PostStatus.PENDING);
        Post rejected = post(author, PostStatus.REJECTED);
        Post failed = post(author, PostStatus.FAILED);
        Post expired = post(author, PostStatus.EXPIRED);
        Post deleted = post(author, PostStatus.DONE);
        Post old = post(author, PostStatus.DONE);
        Post othersPost = post(other, PostStatus.DONE);
        flushAndClear();
        setColumn(done, "created_at", now.minusMinutes(1));
        setColumn(pending, "created_at", now.minusMinutes(2));
        setColumn(rejected, "created_at", now.minusMinutes(3));
        setColumn(failed, "created_at", now.minusMinutes(4));
        setColumn(expired, "created_at", now.minusMinutes(5));
        setColumn(deleted, "created_at", now.minusMinutes(6));
        setColumn(old, "created_at", now.minusMinutes(11));
        setColumn(othersPost, "created_at", now.minusMinutes(1));
        em.createNativeQuery("update post set is_active = false where post_id = :id")
                .setParameter("id", deleted.getPostId()).executeUpdate();

        List<LocalDateTime> all = postJpaRepository.findRecentUploadTimes(author.getUserId(), now.minusMinutes(10),
                PostUploadLimitService.COUNTED_STATUSES, Limit.of(10));
        List<LocalDateTime> limited = postJpaRepository.findRecentUploadTimes(author.getUserId(), now.minusMinutes(10),
                PostUploadLimitService.COUNTED_STATUSES, Limit.of(2));

        // FAILED, EXPIRED, 기간 밖(11분 전), 다른 사람 글은 빠지고 삭제한 글은 센다
        assertThat(all).containsExactly(now.minusMinutes(1), now.minusMinutes(2), now.minusMinutes(3),
                now.minusMinutes(6));
        assertThat(limited).containsExactly(now.minusMinutes(1), now.minusMinutes(2));
    }

    @Test
    void 심사_대기는_삭제하지_않은_PENDING만_센다() {
        Users author = user("author");
        Users other = user("other");
        post(author, PostStatus.PENDING);
        post(author, PostStatus.PENDING);
        Post deletedPending = post(author, PostStatus.PENDING);
        post(author, PostStatus.DONE);
        post(author, PostStatus.FAILED);
        post(other, PostStatus.PENDING);
        flushAndClear();
        em.createNativeQuery("update post set is_active = false where post_id = :id")
                .setParameter("id", deletedPending.getPostId()).executeUpdate();

        assertThat(postJpaRepository.countActivePendingByUserId(author.getUserId())).isEqualTo(2);
    }

    // ===== helpers =====

    private void setColumn(Post post, String column, LocalDateTime value) {
        em.createNativeQuery("update post set " + column + " = :value where post_id = :id")
                .setParameter("value", value)
                .setParameter("id", post.getPostId())
                .executeUpdate();
    }

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
