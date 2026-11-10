package com.raon.tikitaka.adapter;

import com.raon.tikitaka.adapter.board.out.BoardJpaRepository;
import com.raon.tikitaka.adapter.post.out.PostJpaRepository;
import com.raon.tikitaka.adapter.post.out.PostPersistenceAdapter;
import com.raon.tikitaka.adapter.user.out.UserJpaRepository;
import com.raon.tikitaka.application.block.out.BlockRepositoryPort;
import com.raon.tikitaka.application.equipment.in.GetEquippedItemsUseCase;
import com.raon.tikitaka.application.post.PostPage;
import com.raon.tikitaka.application.post.PostService;
import com.raon.tikitaka.application.post.PostSummary;
import com.raon.tikitaka.application.post.out.PostLikeRepositoryPort;
import com.raon.tikitaka.application.ranking.out.RankingRepositoryPort;
import com.raon.tikitaka.application.storage.in.StorageUseCase;
import com.raon.tikitaka.application.territory.in.CheckRankingFrozenUseCase;
import com.raon.tikitaka.domain.block.UserBlock;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 글 목록 커서 페이징을 실제 PostgreSQL에서 돌려 본다. PostService와 실제 PostPersistenceAdapter를 붙여
 * 커서 인코딩·해석부터 keyset 쿼리까지 한 번에 지난다(좋아요·이미지·착용 정보 포트는 mock).
 * 페이지를 끝까지 넘긴 결과가 파라미터 없는 전체 목록과 같은 순서로 빠짐없이, 중복 없이 나와야 한다.
 * WithdrawnUserQueryTest와 같이 TEST_DATABASE_URL이 없으면 건너뛴다.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class PostPagingQueryDbTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("TEST_DATABASE_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("TEST_DATABASE_PASSWORD"));
    }

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 11, 10, 10, 0, 0);

    @Autowired EntityManager em;
    @Autowired PostJpaRepository postJpaRepository;
    @Autowired BoardJpaRepository boardJpaRepository;
    @Autowired UserJpaRepository userJpaRepository;

    private PostService postService;
    private Location home;
    private Board board;
    private Users author;
    private Users other;
    private Users viewer;

    @BeforeEach
    void setUp() {
        postService = new PostService(
                new PostPersistenceAdapter(postJpaRepository, boardJpaRepository, userJpaRepository),
                mock(PostLikeRepositoryPort.class), mock(RankingRepositoryPort.class), null,
                mock(BlockRepositoryPort.class), null, mock(CheckRankingFrozenUseCase.class),
                mock(StorageUseCase.class), mock(GetEquippedItemsUseCase.class));

        home = location("해운대구");
        Location away = location("수영구");
        Stage stage = Stage.create(1, 1, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(6));
        em.persist(stage);
        Match match = Match.create(stage, home, away, MatchType.NORMAL, 1, 1, "미션");
        em.persist(match);
        board = Board.create(match);
        em.persist(board);
        author = user("author");
        other = user("other");
        viewer = user("viewer");
    }

    @Test
    void 끝까지_넘기면_전체_목록과_같은_순서로_빠짐없이_중복없이_나온다() {
        for (int i = 0; i < 23; i++) {
            post(i % 2 == 0 ? author : other, BASE.plusNanos(i * 1_000L));
        }
        flushAndClear();

        for (UUID viewerId : new UUID[]{null, viewer.getUserId()}) {
            List<UUID> full = ids(postService.getPosts(board.getBoardId(), viewerId));
            for (int size : new int[]{1, 3, 5, 23, 50}) {
                assertThat(walk(viewerId, size)).as("viewer=%s size=%d", viewerId, size).isEqualTo(full);
            }
            assertThat(full).hasSize(23);
        }
    }

    @Test
    void 마이크로초만_다른_글과_완전히_같은_시각의_글이_페이지_경계에서_빠지거나_겹치지_않는다() {
        // 같은 밀리초 안에서 마이크로초만 다른 글 5개와, 마이크로초까지 같은 글 7개
        for (int i = 1; i <= 5; i++) {
            post(author, BASE.plusNanos(i * 1_000L));
        }
        for (int i = 0; i < 7; i++) {
            post(i % 2 == 0 ? author : other, BASE.plusNanos(3_000L));
        }
        flushAndClear();

        List<UUID> full = ids(postService.getPosts(board.getBoardId(), null));
        assertThat(full).hasSize(12);
        for (int size = 1; size <= 6; size++) {
            assertThat(walk(null, size)).as("size=%d", size).isEqualTo(full);
        }
    }

    @Test
    void 넘기는_사이_새_글이_올라와도_이미_본_글이_다시_나오지_않는다() {
        for (int i = 0; i < 9; i++) {
            post(author, BASE.plusSeconds(i));
        }
        flushAndClear();
        List<UUID> before = ids(postService.getPosts(board.getBoardId(), null));

        PostPage first = postService.getPostPage(board.getBoardId(), null, 3, null);
        // 첫 페이지를 본 뒤 가장 최신 글이 새로 올라온다
        post(other, BASE.plusDays(1));
        flushAndClear();
        List<UUID> rest = walkFrom(null, 3, first.nextCursor());

        List<UUID> seen = new ArrayList<>(ids(first.posts()));
        seen.addAll(rest);
        assertThat(seen).isEqualTo(before);
        assertThat(new HashSet<>(seen)).hasSameSizeAs(seen);
    }

    @Test
    void 커서가_가리키던_글이_삭제돼도_다음_페이지가_이어진다() {
        for (int i = 0; i < 9; i++) {
            post(author, BASE.plusSeconds(i));
        }
        flushAndClear();
        List<UUID> before = ids(postService.getPosts(board.getBoardId(), null));

        PostPage first = postService.getPostPage(board.getBoardId(), null, 3, null);
        UUID cursorPostId = ids(first.posts()).get(2);
        em.find(Post.class, cursorPostId).deactivate();
        flushAndClear();
        List<UUID> rest = walkFrom(null, 3, first.nextCursor());

        assertThat(rest).isEqualTo(before.subList(3, 9));
    }

    @Test
    void 차단한_유저의_글이_섞여_있어도_마지막_페이지_전까지_size만큼_채워진다() {
        Users blocked = user("blocked");
        em.persist(UserBlock.of(viewer, blocked));
        for (int i = 0; i < 20; i++) {
            // 차단한 유저의 글이 절반, 연속으로 몰린 구간도 있다
            post(i % 2 == 0 || (i >= 10 && i < 15) ? blocked : author, BASE.plusSeconds(i));
        }
        flushAndClear();

        List<Integer> sizes = new ArrayList<>();
        List<UUID> seen = new ArrayList<>();
        String cursor = null;
        do {
            PostPage page = postService.getPostPage(board.getBoardId(), viewer.getUserId(), 3, cursor);
            sizes.add(page.posts().size());
            seen.addAll(ids(page.posts()));
            assertThat(page.posts()).noneMatch(s -> s.post().getUserId().getUserId().equals(blocked.getUserId()));
            cursor = page.nextCursor();
        } while (cursor != null);

        assertThat(seen).isEqualTo(ids(postService.getPosts(board.getBoardId(), viewer.getUserId())));
        assertThat(seen).hasSize(8);
        assertThat(sizes.subList(0, sizes.size() - 1)).containsOnly(3);
    }

    @Test
    void 탈퇴한_작성자_PENDING_REJECTED_비활성_글은_페이지에_섞이지_않는다() {
        Users withdrawn = user("withdrawn");
        List<UUID> visible = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            Post post = switch (i % 4) {
                case 0 -> post(withdrawn, BASE.plusSeconds(i));
                case 1 -> post(author, BASE.plusSeconds(i), PostStatus.PENDING, true);
                case 2 -> post(author, BASE.plusSeconds(i), PostStatus.REJECTED, true);
                default -> post(author, BASE.plusSeconds(i));
            };
            if (i % 4 == 3) {
                visible.add(post.getPostId());
            }
        }
        post(author, BASE.plusSeconds(100), PostStatus.DONE, false);
        em.createNativeQuery("update users set status = 'WITHDRAWN' where user_id = :id")
                .setParameter("id", withdrawn.getUserId())
                .executeUpdate();
        flushAndClear();

        List<UUID> paged = walk(viewer.getUserId(), 2);

        assertThat(paged).containsExactlyInAnyOrderElementsOf(visible);
        assertThat(paged).isEqualTo(ids(postService.getPosts(board.getBoardId(), viewer.getUserId())));
    }

    @Test
    void 마지막_페이지에_정확히_size개가_남으면_next_cursor가_없다() {
        for (int i = 0; i < 6; i++) {
            post(author, BASE.plusSeconds(i));
        }
        flushAndClear();

        PostPage first = postService.getPostPage(board.getBoardId(), null, 3, null);
        PostPage second = postService.getPostPage(board.getBoardId(), null, 3, first.nextCursor());

        assertThat(first.posts()).hasSize(3);
        assertThat(first.nextCursor()).isNotNull();
        assertThat(second.posts()).hasSize(3);
        assertThat(second.nextCursor()).isNull();
    }

    @Test
    void 첫_페이지는_로그인_여부와_상관없이_null_파라미터_오류_없이_조회된다() {
        post(author, BASE);
        flushAndClear();

        assertThat(postService.getPostPage(board.getBoardId(), null, 1, null).posts()).hasSize(1);
        assertThat(postService.getPostPage(board.getBoardId(), viewer.getUserId(), 1, null).posts()).hasSize(1);
    }

    // ===== helpers =====

    private List<UUID> walk(UUID viewerId, int size) {
        return walkFrom(viewerId, size, null);
    }

    private List<UUID> walkFrom(UUID viewerId, int size, String cursor) {
        List<UUID> seen = new ArrayList<>();
        int guard = 0;
        do {
            PostPage page = postService.getPostPage(board.getBoardId(), viewerId, size, cursor);
            assertThat(page.posts().size()).isLessThanOrEqualTo(size);
            seen.addAll(ids(page.posts()));
            cursor = page.nextCursor();
            assertThat(++guard).as("페이지가 끝나지 않음").isLessThan(1000);
        } while (cursor != null);
        return seen;
    }

    private static List<UUID> ids(List<PostSummary> summaries) {
        return summaries.stream().map(s -> s.post().getPostId()).toList();
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

    private Post post(Users writer, LocalDateTime createdAt) {
        return post(writer, createdAt, PostStatus.DONE, true);
    }

    /**
     * created_at은 저장 시 자동으로 채워지므로 저장 뒤 네이티브 update로 원하는 값(마이크로초 포함)을 넣는다.
     * 상태와 활성 여부는 저장 전에 정한다. 저장 뒤에 바꾸면 flush가 행 전체를 다시 써서 created_at이 덮인다.
     */
    private Post post(Users writer, LocalDateTime createdAt, PostStatus status, boolean active) {
        Post post = Post.create(writer, board, "내용", null, status == PostStatus.DONE ? 80 : null, null,
                home.getLocationName(), home, true);
        ReflectionTestUtils.setField(post, "status", status);
        if (!active) {
            post.deactivate();
        }
        em.persist(post);
        em.flush();
        em.createNativeQuery("update post set created_at = :createdAt where post_id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", post.getPostId())
                .executeUpdate();
        return post;
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }
}
