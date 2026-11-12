package com.raon.tikitaka.adapter.post;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.raon.tikitaka.application.block.out.BlockRepositoryPort;
import com.raon.tikitaka.application.board.in.GetBoardUseCase;
import com.raon.tikitaka.application.equipment.EquippedItems;
import com.raon.tikitaka.application.equipment.in.GetEquippedItemsUseCase;
import com.raon.tikitaka.application.post.PostPage;
import com.raon.tikitaka.application.post.PostService;
import com.raon.tikitaka.application.post.PostSummary;
import com.raon.tikitaka.application.post.in.CheckPostUploadLimitUseCase;
import com.raon.tikitaka.application.post.in.CreatePostUseCase;
import com.raon.tikitaka.application.post.in.DeletePostUseCase;
import com.raon.tikitaka.application.post.in.GetPostDetailUseCase;
import com.raon.tikitaka.application.post.in.GetPostListUseCase;
import com.raon.tikitaka.application.post.in.LikePostUseCase;
import com.raon.tikitaka.application.post.in.UnlikePostUseCase;
import com.raon.tikitaka.application.post.in.UpdatePostUseCase;
import com.raon.tikitaka.application.post.out.PostLikeRepositoryPort;
import com.raon.tikitaka.application.post.out.PostRepositoryPort;
import com.raon.tikitaka.application.ranking.out.RankingRepositoryPort;
import com.raon.tikitaka.application.review.in.ReviewUseCase;
import com.raon.tikitaka.application.storage.in.StorageUseCase;
import com.raon.tikitaka.application.territory.in.CheckRankingFrozenUseCase;
import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.domain.post.Post;
import com.raon.tikitaka.domain.user.Users;
import com.raon.tikitaka.global.config.PostReviewProperties;
import com.raon.tikitaka.global.guard.RequestGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 글 목록의 size, cursor 처리. 둘 다 없으면 기존 전체 목록이고 next_cursor가 없다. 하나라도 있으면 페이징이다.
 * 400 경우는 실제 PostService를 붙인 컨트롤러로 끝까지 확인한다(포트는 mock이고 조회 전에 끝나야 한다).
 * 직렬화 설정은 application.yaml의 spring.jackson(SNAKE_CASE, non_null)과 같게 맞춘다.
 */
class PostControllerPagingTest {

    private static final Long BOARD_ID = 3L;

    private final JsonMapper jsonMapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
            .build();

    private final GetPostListUseCase getPostListUseCase = mock(GetPostListUseCase.class);
    private final PostRepositoryPort postRepositoryPort = mock(PostRepositoryPort.class);
    private PostController controller;
    private PostController controllerWithService;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        controller = controller(getPostListUseCase);
        PostService postService = new PostService(postRepositoryPort, mock(PostLikeRepositoryPort.class),
                mock(RankingRepositoryPort.class), null, mock(BlockRepositoryPort.class), null,
                mock(CheckRankingFrozenUseCase.class), mock(StorageUseCase.class), mock(GetEquippedItemsUseCase.class));
        controllerWithService = controller(postService);
    }

    // ===== 모드 선택 =====

    @Test
    void 파라미터가_없으면_기존_전체_목록이고_next_cursor가_없다() {
        Post post = post();
        when(getPostListUseCase.getPosts(BOARD_ID, userId)).thenReturn(List.of(summary(post)));

        JsonNode data = json(controller.getPosts(userId, BOARD_ID, null, null)).get("data");

        assertThat(data.get("post")).hasSize(1);
        assertThat(data.has("next_cursor")).isFalse();
        verify(getPostListUseCase, never()).getPostPage(anyLong(), any(), any(), any());
    }

    @Test
    void size만_주면_첫_페이지다() {
        when(getPostListUseCase.getPostPage(BOARD_ID, userId, 5, null)).thenReturn(new PostPage(List.of(), null));

        controller.getPosts(userId, BOARD_ID, "5", null);

        verify(getPostListUseCase).getPostPage(BOARD_ID, userId, 5, null);
        verify(getPostListUseCase, never()).getPosts(anyLong(), any());
    }

    @Test
    void cursor만_주면_size는_기본값으로_넘긴다() {
        when(getPostListUseCase.getPostPage(BOARD_ID, userId, null, "abc")).thenReturn(new PostPage(List.of(), null));

        controller.getPosts(userId, BOARD_ID, null, "abc");

        verify(getPostListUseCase).getPostPage(BOARD_ID, userId, null, "abc");
    }

    @Test
    void 둘_다_주면_그대로_넘기고_next_cursor가_최상위에_들어간다() {
        Post post = post();
        when(getPostListUseCase.getPostPage(BOARD_ID, userId, 3, "abc"))
                .thenReturn(new PostPage(List.of(summary(post)), "next-value"));

        JsonNode data = json(controller.getPosts(userId, BOARD_ID, "3", "abc")).get("data");

        assertThat(data.get("next_cursor").asString()).isEqualTo("next-value");
        assertThat(data.has("nextCursor")).isFalse();
        assertThat(data.get("post")).hasSize(1);
        // 각 글 항목의 필드는 그대로다
        assertThat(data.get("post").get(0).get("post_image").asString()).isEqualTo("key.jpg");
        assertThat(data.get("post").get(0).get("post_image_url").asString()).isEqualTo("https://example.com/key.jpg");
        assertThat(data.get("post").get(0).has("user_equipment")).isTrue();
    }

    @Test
    void 마지막_페이지면_next_cursor가_빠진다() {
        when(getPostListUseCase.getPostPage(BOARD_ID, userId, 3, null)).thenReturn(new PostPage(List.of(), null));

        JsonNode data = json(controller.getPosts(userId, BOARD_ID, "3", null)).get("data");

        assertThat(data.has("next_cursor")).isFalse();
        assertThat(data.get("post")).isEmpty();
    }

    @Test
    void 비로그인도_페이징이_동작한다() {
        when(getPostListUseCase.getPostPage(BOARD_ID, null, 3, null)).thenReturn(new PostPage(List.of(), "next"));

        JsonNode data = json(controller.getPosts(null, BOARD_ID, "3", null)).get("data");

        assertThat(data.get("next_cursor").asString()).isEqualTo("next");
        verify(getPostListUseCase).getPostPage(BOARD_ID, null, 3, null);
    }

    // ===== 400 =====

    @ParameterizedTest
    @ValueSource(strings = {"0", "51", "-1"})
    void size가_범위를_벗어나면_400(String size) {
        assertBadRequest(() -> controllerWithService.getPosts(userId, BOARD_ID, size, null),
                "size는 1 이상 50 이하여야 합니다.");
        assertBadRequest(() -> controllerWithService.getPosts(null, BOARD_ID, size, null),
                "size는 1 이상 50 이하여야 합니다.");
        verifyNoInteractions(postRepositoryPort);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1.5", "3a", "99999999999"})
    void size가_숫자가_아니면_400(String size) {
        assertBadRequest(() -> controllerWithService.getPosts(userId, BOARD_ID, size, null), "size는 숫자여야 합니다.");
        verifyNoInteractions(postRepositoryPort);
    }

    @ParameterizedTest
    @ValueSource(strings = {"!!bad", "aGVsbG8", "not-a-cursor"})
    void 잘못된_cursor는_400(String cursor) {
        assertBadRequest(() -> controllerWithService.getPosts(userId, BOARD_ID, null, cursor), "잘못된 cursor입니다.");
        assertBadRequest(() -> controllerWithService.getPosts(null, BOARD_ID, "3", cursor), "잘못된 cursor입니다.");
        verifyNoInteractions(postRepositoryPort);
    }

    // ===== helpers =====

    private PostController controller(GetPostListUseCase useCase) {
        return new PostController(useCase, mock(GetPostDetailUseCase.class),
                mock(CreatePostUseCase.class), mock(UpdatePostUseCase.class), mock(DeletePostUseCase.class),
                mock(LikePostUseCase.class), mock(UnlikePostUseCase.class), mock(StorageUseCase.class),
                mock(ReviewUseCase.class), mock(GetBoardUseCase.class), new RequestGuard(),
                new PostReviewProperties(false, 1, 4, 5, Duration.ofMinutes(1), Duration.ofMinutes(5)),
                mock(CheckPostUploadLimitUseCase.class));
    }

    private static void assertBadRequest(Runnable call, String message) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getReason()).isEqualTo(message);
                });
    }

    private JsonNode json(Object response) {
        return jsonMapper.readTree(jsonMapper.writeValueAsString(response));
    }

    private static PostSummary summary(Post post) {
        return new PostSummary(post, 0, false, "https://example.com/key.jpg", null, null, EquippedItems.empty());
    }

    private Post post() {
        Users author = mock(Users.class);
        lenient().when(author.getUserId()).thenReturn(userId);
        lenient().when(author.getUserName()).thenReturn("작성자");
        Location team = mock(Location.class);
        lenient().when(team.getCityName()).thenReturn("부산광역시");
        Post post = mock(Post.class);
        lenient().when(post.getPostId()).thenReturn(UUID.randomUUID());
        lenient().when(post.getUserId()).thenReturn(author);
        lenient().when(post.getPostImage()).thenReturn("key.jpg");
        lenient().when(post.getTeamLocation()).thenReturn(team);
        return post;
    }
}
