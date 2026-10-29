package com.raon.tikitaka.adapter;

import com.raon.tikitaka.adapter.block.out.BlockPersistenceAdapter;
import com.raon.tikitaka.adapter.location.out.LocationPersistenceAdapter;
import com.raon.tikitaka.adapter.match.out.StagePersistenceAdapter;
import com.raon.tikitaka.adapter.post.out.PostJpaRepository;
import com.raon.tikitaka.adapter.post.out.PostLikePersistenceAdapter;
import com.raon.tikitaka.adapter.post.out.PostPersistenceAdapter;
import com.raon.tikitaka.adapter.post.out.PostReviewQueuePersistenceAdapter;
import com.raon.tikitaka.adapter.ranking.out.RankingPersistenceAdapter;
import com.raon.tikitaka.adapter.ranking.out.SeasonResultPersistenceAdapter;
import com.raon.tikitaka.adapter.review.out.GeminiAdapterTestAccess;
import com.raon.tikitaka.application.post.PostService;
import com.raon.tikitaka.application.post.review.ClaimedReview;
import com.raon.tikitaka.application.post.out.PostReviewQueuePort;
import com.raon.tikitaka.application.post.review.PostReviewProcessor;
import com.raon.tikitaka.application.post.review.PostReviewQueueService;
import com.raon.tikitaka.application.ranking.RankingService;
import com.raon.tikitaka.application.ranking.out.SeasonResultRepositoryPort;
import com.raon.tikitaka.application.review.ReviewService;
import com.raon.tikitaka.application.review.out.AiReviewPort;
import com.raon.tikitaka.application.storage.StoredImage;
import com.raon.tikitaka.application.storage.out.StoragePort;
import com.raon.tikitaka.domain.board.Board;
import com.raon.tikitaka.domain.enums.LoginProvider;
import com.raon.tikitaka.domain.enums.MatchType;
import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.domain.match.Match;
import com.raon.tikitaka.domain.match.Stage;
import com.raon.tikitaka.domain.post.Post;
import com.raon.tikitaka.domain.post.PostStatus;
import com.raon.tikitaka.domain.user.Users;
import com.raon.tikitaka.global.config.PostReviewProperties;
import com.raon.tikitaka.global.config.RankingProperties;
import com.raon.tikitaka.global.config.SeasonProperties;
import com.raon.tikitaka.global.guard.RequestGuard;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 커밋되는 트랜잭션 여러 개가 같은 글 행을 두고 겹치는 상황을 실제 PostgreSQL에서 돌려 본다.
 * 테스트마다 데이터를 커밋하므로 끝나면 테이블을 비운다. TEST_DATABASE_URL이 없으면 건너뛴다.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({PostService.class, PostPersistenceAdapter.class, PostLikePersistenceAdapter.class,
        RankingPersistenceAdapter.class, BlockPersistenceAdapter.class, PostReviewQueuePersistenceAdapter.class,
        PostReviewQueueService.class, RankingService.class, StagePersistenceAdapter.class,
        SeasonResultPersistenceAdapter.class, LocationPersistenceAdapter.class})
@EnableConfigurationProperties({RankingProperties.class, PostReviewProperties.class, SeasonProperties.class})
class PostReviewConcurrencyTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("TEST_DATABASE_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("TEST_DATABASE_PASSWORD"));
    }

    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired PostService postService;
    @Autowired PostReviewQueueService postReviewQueueService;
    @Autowired PostJpaRepository postJpaRepository;
    @Autowired PostReviewQueuePort postReviewQueuePort;
    @Autowired RankingService rankingService;
    @Autowired SeasonResultRepositoryPort seasonResultRepositoryPort;

    private TransactionTemplate tx;
    private Long homeId;
    private Long stageId;
    private Long boardId;
    private UUID authorId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            Location home = location("해운대구");
            Location away = location("수영구");
            Stage stage = Stage.create(1, 1, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(6));
            stage.assignAvgLocationMemberCount(5.0);
            em.persist(stage);
            Match match = Match.create(stage, home, away, MatchType.NORMAL, 5, 5, "미션");
            em.persist(match);
            Board board = Board.create(match);
            em.persist(board);
            Users author = Users.of("author", LoginProvider.KAKAO, "author-provider", home);
            em.persist(author);
            homeId = home.getLocationId();
            stageId = stage.getStageId();
            boardId = board.getBoardId();
            authorId = author.getUserId();
        });
    }

    @AfterEach
    void tearDown() {
        tx.executeWithoutResult(status -> em.createNativeQuery("""
                truncate table post_like, report, user_block, user_ranking, location_ranking, season_location_result,
                               post, board, match, stage, token, users, location restart identity cascade
                """).executeUpdate());
    }

    // ===== 대기열 선점 =====

    @Test
    void 다른_트랜잭션이_잠근_글은_건너뛰고_다음_글을_선점한다() throws Exception {
        UUID oldest = pendingPost(LocalDateTime.now().minusMinutes(3));
        UUID next = pendingPost(LocalDateTime.now().minusMinutes(2));
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> tx.executeWithoutResult(status -> {
            postJpaRepository.findByIdForUpdate(oldest);
            locked.countDown();
            await(release);
        }));
        assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();

        // 잠긴 글을 기다리지 않고 바로 다음 글을 가져간다
        List<ClaimedReview> claimed = postReviewQueueService.claim(1);
        release.countDown();
        holder.get(5, TimeUnit.SECONDS);

        assertThat(claimed).extracting(ClaimedReview::postId).containsExactly(next);
    }

    @Test
    void 선점_시각은_DB에_저장했다_읽어도_같은_값이다() {
        UUID postId = pendingPost(LocalDateTime.now());

        ClaimedReview claimed = postReviewQueueService.claim(1).get(0);

        assertThat(claimed.claimedAt().getNano() % 1_000).isZero();
        assertThat(reviewStartedAt(postId)).isEqualTo(claimed.claimedAt());
    }

    @Test
    void 선점이_만료돼_다른_작업이_다시_가져가면_앞_작업은_상태와_선점을_바꾸지_못한다() {
        UUID postId = pendingPost(LocalDateTime.now());
        ClaimedReview first = postReviewQueueService.claim(1).get(0);
        // 앞 작업이 5분 넘게 끝나지 않은 상황. 선점 시각을 과거로 돌려 만료시킨다
        inTx(() -> em.createNativeQuery("update post set review_started_at = :at where post_id = :postId")
                .setParameter("at", first.claimedAt().minusMinutes(6))
                .setParameter("postId", postId)
                .executeUpdate());
        ClaimedReview second = postReviewQueueService.claim(1).get(0);
        assertThat(second.postId()).isEqualTo(postId);

        // 늦게 끝난 앞 작업의 기록은 모두 무시된다
        assertThat(postReviewQueueService.recordFailure(postId, first.claimedAt(), null)).isNull();
        assertThat(postReviewQueueService.finish(postId, first.claimedAt(), PostStatus.REJECTED)).isFalse();
        assertThat(postReviewQueueService.release(postId, first.claimedAt())).isFalse();
        assertThat(postStatus(postId)).isEqualTo(PostStatus.PENDING);
        assertThat(reviewStartedAt(postId)).isEqualTo(second.claimedAt());
        assertThat(reviewAttempts(postId)).isZero();

        // 지금 선점한 작업은 그대로 기록한다
        assertThat(postReviewQueueService.recordFailure(postId, second.claimedAt(), null))
                .isEqualTo(PostStatus.PENDING);
        assertThat(reviewAttempts(postId)).isEqualTo(1);
        assertThat(reviewStartedAt(postId)).isNull();
    }

    // ===== 탈퇴한 작성자 =====

    @Test
    void 탈퇴한_작성자의_PENDING_글도_DONE이_되고_점수_포인트_랭킹이_반영되며_조회에서는_계속_빠진다() {
        UUID postId = pendingPost(LocalDateTime.now());
        tx.executeWithoutResult(status -> em.find(Users.class, authorId).withdraw());

        boolean applied = postService.completeReview(postId, 80, "좋아요");

        assertThat(applied).isTrue();
        assertThat(postStatus(postId)).isEqualTo(PostStatus.DONE);
        assertThat(userScore()).isEqualTo(5421L);
        assertThat(locationScore()).isEqualTo(5421L);
        assertThat(point()).isEqualTo(271);
        assertThat(inTx(() -> postJpaRepository.findAllActiveByBoardId(boardId))).isEmpty();
        assertThat(inTx(() -> postJpaRepository.findActiveById(postId))).isEmpty();
    }

    // ===== 심사 완료와 삭제의 경합 =====

    @Test
    void 심사_완료가_먼저_잠그면_삭제는_기다렸다가_더해진_점수를_그대로_뺀다() throws Exception {
        UUID postId = pendingPost(LocalDateTime.now());
        CountDownLatch completed = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        CompletableFuture<Void> reviewing = CompletableFuture.runAsync(() -> tx.executeWithoutResult(status -> {
            // 바깥 트랜잭션에 합류시켜 반영 후 커밋 전에 멈춰 둔다
            assertThat(postService.completeReview(postId, 80, "좋아요")).isTrue();
            completed.countDown();
            await(commit);
        }));
        assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Void> deleting = CompletableFuture.runAsync(
                () -> postService.deletePost(postId, authorId, false));
        Thread.sleep(300);
        assertThat(deleting).isNotDone();      // 같은 글 행 잠금을 기다린다

        commit.countDown();
        reviewing.get(5, TimeUnit.SECONDS);
        deleting.get(5, TimeUnit.SECONDS);

        assertThat(userScore()).isZero();
        assertThat(locationScore()).isZero();
        assertThat(isActive(postId)).isFalse();
    }

    @Test
    void 삭제가_먼저_잠그면_심사_완료는_아무것도_반영하지_않는다() throws Exception {
        UUID postId = pendingPost(LocalDateTime.now());
        CountDownLatch deleted = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        CompletableFuture<Void> deleting = CompletableFuture.runAsync(() -> tx.executeWithoutResult(status -> {
            postService.deletePost(postId, authorId, false);
            deleted.countDown();
            await(commit);
        }));
        assertThat(deleted.await(5, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Boolean> reviewing = CompletableFuture.supplyAsync(
                () -> postService.completeReview(postId, 80, "좋아요"));
        Thread.sleep(300);
        assertThat(reviewing).isNotDone();

        commit.countDown();
        deleting.get(5, TimeUnit.SECONDS);

        assertThat(reviewing.get(5, TimeUnit.SECONDS)).isFalse();
        assertThat(userScoreRows()).isZero();
        assertThat(point()).isZero();
        assertThat(postStatus(postId)).isEqualTo(PostStatus.PENDING);
    }

    @Test
    void 같은_글에_심사_완료가_동시에_두_번_와도_한_번만_반영된다() throws Exception {
        UUID postId = pendingPost(LocalDateTime.now());

        CompletableFuture<Boolean> first = CompletableFuture.supplyAsync(
                () -> postService.completeReview(postId, 80, "좋아요"));
        CompletableFuture<Boolean> second = CompletableFuture.supplyAsync(
                () -> postService.completeReview(postId, 80, "좋아요"));

        assertThat(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(true, false);
        assertThat(userScore()).isEqualTo(5421L);
        assertThat(point()).isEqualTo(271);
    }

    // ===== 안전 필터 차단 =====

    @Test
    void 안전_필터에_막힌_글은_한_번만_심사하고_재시도_예약_없이_REJECTED가_된다() {
        UUID postId = pendingPost(LocalDateTime.now());
        AtomicInteger calls = new AtomicInteger();
        AiReviewPort blockedGemini = (mission, content, image, contentType) -> {
            calls.incrementAndGet();
            return GeminiAdapterTestAccess.parse("{\"promptFeedback\": {\"blockReason\": \"PROHIBITED_CONTENT\"}}");
        };
        StoragePort storage = mock(StoragePort.class);
        when(storage.download("photo.jpg")).thenReturn(new StoredImage(new byte[]{1}, "image/jpeg"));
        PostReviewProcessor processor = new PostReviewProcessor(postReviewQueueService, postService,
                new ReviewService(blockedGemini), storage, seasonResultRepositoryPort, new RequestGuard());

        List<ClaimedReview> claimed = postReviewQueueService.claim(1);
        processor.review(claimed.get(0));

        assertThat(calls).hasValue(1);
        assertThat(postStatus(postId)).isEqualTo(PostStatus.REJECTED);
        Object[] review = inTx(() -> (Object[]) em.createNativeQuery(
                        "select review_attempts, review_next_at, review_started_at from post where post_id = :postId")
                .setParameter("postId", postId)
                .getSingleResult());
        assertThat(((Number) review[0]).intValue()).isZero();
        assertThat(review[1]).isNull();
        assertThat(review[2]).isNull();
        // 다시 선점되지 않는다
        assertThat(postReviewQueueService.claim(1)).isEmpty();
        assertThat(userScoreRows()).isZero();
    }

    // ===== 시즌 확정 =====

    @Test
    void 시즌_확정_시_그_시즌의_PENDING_글은_EXPIRED가_되고_점수_포인트_랭킹에_반영되지_않는다() {
        Long endedBoardId = endedSeason(9);
        UUID expiring = pendingPost(endedBoardId, LocalDateTime.now());
        UUID otherSeason = pendingPost(LocalDateTime.now());      // setUp의 시즌 1, 진행 중

        rankingService.execute();

        assertThat(postStatus(expiring)).isEqualTo(PostStatus.EXPIRED);
        assertThat(postStatus(otherSeason)).isEqualTo(PostStatus.PENDING);
        assertThat(seasonResultRows(9)).isEqualTo(2);
        // 확정 뒤에 워커가 늦게 심사를 끝내도 반영되지 않는다
        assertThat(postService.completeReview(expiring, 80, "좋아요")).isFalse();
        assertThat(userScoreRows()).isZero();
        assertThat(point()).isZero();
    }

    @Test
    void 확정과_심사_완료가_겹칠_때_심사_완료가_먼저면_확정이_기다렸다가_그_점수를_계산에_넣는다() throws Exception {
        Long endedBoardId = endedSeason(9);
        UUID postId = pendingPost(endedBoardId, LocalDateTime.now());
        CountDownLatch completed = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        CompletableFuture<Void> reviewing = CompletableFuture.runAsync(() -> tx.executeWithoutResult(status -> {
            assertThat(postService.completeReview(postId, 80, "좋아요")).isTrue();
            completed.countDown();
            await(commit);
        }));
        assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Void> finalizing = CompletableFuture.runAsync(() -> rankingService.execute());
        Thread.sleep(300);
        assertThat(finalizing).isNotDone();     // EXPIRED update가 글 행 잠금을 기다린다

        commit.countDown();
        reviewing.get(5, TimeUnit.SECONDS);
        finalizing.get(5, TimeUnit.SECONDS);

        assertThat(postStatus(postId)).isEqualTo(PostStatus.DONE);
        assertThat(seasonFinalScore(9, homeId)).isEqualTo(5421L);
    }

    @Test
    void 확정과_심사_완료가_겹칠_때_확정이_먼저면_심사_완료는_아무것도_반영하지_않는다() throws Exception {
        Long endedBoardId = endedSeason(9);
        UUID postId = pendingPost(endedBoardId, LocalDateTime.now());
        CountDownLatch expired = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        CompletableFuture<Void> finalizing = CompletableFuture.runAsync(() -> tx.executeWithoutResult(status -> {
            assertThat(postReviewQueuePort.expirePendingBySeason(9)).isEqualTo(1);
            expired.countDown();
            await(commit);
        }));
        assertThat(expired.await(5, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Boolean> reviewing = CompletableFuture.supplyAsync(
                () -> postService.completeReview(postId, 80, "좋아요"));
        Thread.sleep(300);
        assertThat(reviewing).isNotDone();

        commit.countDown();
        finalizing.get(5, TimeUnit.SECONDS);

        assertThat(reviewing.get(5, TimeUnit.SECONDS)).isFalse();
        assertThat(postStatus(postId)).isEqualTo(PostStatus.EXPIRED);
        assertThat(userScoreRows()).isZero();
        assertThat(point()).isZero();
    }

    // ===== helpers =====

    /**
     * 라운드 4개가 모두 끝난 시즌을 만들고 마지막 라운드 게시판 id를 돌려준다. 확정 대상이 된다.
     */
    private Long endedSeason(int season) {
        return inTx(() -> {
            Location home = em.find(Location.class, homeId);
            Location away = em.createQuery("select l from Location l where l.locationId <> :id", Location.class)
                    .setParameter("id", homeId).getSingleResult();
            LocalDateTime start = LocalDateTime.now().minusDays(40);
            Stage last = null;
            for (int round = 1; round <= 4; round++) {
                Stage stage = Stage.create(season, round, start.plusDays((round - 1) * 7L), start.plusDays(round * 7L));
                stage.assignAvgLocationMemberCount(5.0);
                em.persist(stage);
                last = stage;
            }
            Match match = Match.create(last, home, away, MatchType.NORMAL, 5, 5, "지난 미션");
            em.persist(match);
            Board board = Board.create(match);
            em.persist(board);
            return board.getBoardId();
        });
    }

    private UUID pendingPost(LocalDateTime createdAt) {
        return pendingPost(boardId, createdAt);
    }

    private UUID pendingPost(Long targetBoardId, LocalDateTime createdAt) {
        return inTx(() -> {
            Users author = em.find(Users.class, authorId);
            Location home = em.find(Location.class, homeId);
            Post post = Post.createPending(author, em.find(Board.class, targetBoardId), "내용", "photo.jpg",
                    home.getLocationName(), home, true);
            em.persist(post);
            em.flush();
            em.createNativeQuery("update post set created_at = :createdAt where post_id = :postId")
                    .setParameter("createdAt", createdAt)
                    .setParameter("postId", post.getPostId())
                    .executeUpdate();
            return post.getPostId();
        });
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

    private <T> T inTx(Supplier<T> action) {
        return tx.execute(status -> action.get());
    }

    private PostStatus postStatus(UUID postId) {
        return inTx(() -> PostStatus.valueOf((String) em.createNativeQuery(
                        "select status from post where post_id = :postId")
                .setParameter("postId", postId)
                .getSingleResult()));
    }

    private LocalDateTime reviewStartedAt(UUID postId) {
        return inTx(() -> {
            Object value = em.createNativeQuery("select review_started_at from post where post_id = :postId")
                    .setParameter("postId", postId)
                    .getSingleResult();
            if (value == null) {
                return null;
            }
            return value instanceof java.sql.Timestamp ts ? ts.toLocalDateTime() : (LocalDateTime) value;
        });
    }

    private int reviewAttempts(UUID postId) {
        return inTx(() -> ((Number) em.createNativeQuery("select review_attempts from post where post_id = :postId")
                .setParameter("postId", postId)
                .getSingleResult()).intValue());
    }

    private boolean isActive(UUID postId) {
        return inTx(() -> (Boolean) em.createNativeQuery("select is_active from post where post_id = :postId")
                .setParameter("postId", postId)
                .getSingleResult());
    }

    private long userScore() {
        return inTx(() -> ((Number) em.createNativeQuery(
                        "select coalesce(sum(user_score), 0) from user_ranking where stage_id = :stageId")
                .setParameter("stageId", stageId)
                .getSingleResult()).longValue());
    }

    private long userScoreRows() {
        return inTx(() -> ((Number) em.createNativeQuery("select count(*) from user_ranking")
                .getSingleResult()).longValue());
    }

    private long locationScore() {
        return inTx(() -> ((Number) em.createNativeQuery(
                        "select coalesce(sum(location_score), 0) from location_ranking where stage_id = :stageId")
                .setParameter("stageId", stageId)
                .getSingleResult()).longValue());
    }

    private long seasonResultRows(int season) {
        return inTx(() -> ((Number) em.createNativeQuery(
                        "select count(*) from season_location_result where season = :season")
                .setParameter("season", season)
                .getSingleResult()).longValue());
    }

    private long seasonFinalScore(int season, Long locationId) {
        return inTx(() -> ((Number) em.createNativeQuery(
                        "select final_score from season_location_result where season = :season and location_id = :id")
                .setParameter("season", season)
                .setParameter("id", locationId)
                .getSingleResult()).longValue());
    }

    private int point() {
        return inTx(() -> ((Number) em.createNativeQuery("select point from users where user_id = :userId")
                .setParameter("userId", authorId)
                .getSingleResult()).intValue());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
