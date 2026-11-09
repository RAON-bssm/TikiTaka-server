package com.raon.tikitaka.adapter.post;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.raon.tikitaka.adapter.user.UserInfoController;
import com.raon.tikitaka.application.board.in.GetBoardUseCase;
import com.raon.tikitaka.application.equipment.EquippedItems;
import com.raon.tikitaka.application.post.MyPostSummary;
import com.raon.tikitaka.application.post.PostDetail;
import com.raon.tikitaka.application.post.PostSummary;
import com.raon.tikitaka.application.post.in.CheckPostUploadLimitUseCase;
import com.raon.tikitaka.application.post.in.CreatePostUseCase;
import com.raon.tikitaka.application.post.in.DeletePostUseCase;
import com.raon.tikitaka.application.post.in.GetMyPostsUseCase;
import com.raon.tikitaka.application.post.in.GetPostDetailUseCase;
import com.raon.tikitaka.application.post.in.GetPostListUseCase;
import com.raon.tikitaka.application.post.in.LikePostUseCase;
import com.raon.tikitaka.application.post.in.UnlikePostUseCase;
import com.raon.tikitaka.application.post.in.UpdatePostUseCase;
import com.raon.tikitaka.application.review.in.ReviewUseCase;
import com.raon.tikitaka.application.storage.in.StorageUseCase;
import com.raon.tikitaka.application.user.in.GetMyInfoUseCase;
import com.raon.tikitaka.domain.board.Board;
import com.raon.tikitaka.domain.enums.MatchType;
import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.domain.match.Match;
import com.raon.tikitaka.domain.match.Stage;
import com.raon.tikitaka.domain.post.Post;
import com.raon.tikitaka.domain.post.PostStatus;
import com.raon.tikitaka.domain.user.Users;
import com.raon.tikitaka.global.config.PostReviewProperties;
import com.raon.tikitaka.global.guard.RequestGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 목록·내 게시물·상세 응답의 JSON 필드 이름이 post_image_url인지, 기존 post_image(S3 key)가 그대로인지 본다.
 * 직렬화 설정은 application.yaml의 spring.jackson(SNAKE_CASE, non_null)과 같게 맞춘다.
 */
class PostControllerImageUrlTest {

    private static final String KEY = "1b2c-photo.jpg";
    private static final String URL = "https://bucket.s3.ap-northeast-2.amazonaws.com/1b2c-photo.jpg?X-Amz-Signature=abc";

    private final JsonMapper jsonMapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
            .build();

    private final GetPostListUseCase getPostListUseCase = mock(GetPostListUseCase.class);
    private final GetPostDetailUseCase getPostDetailUseCase = mock(GetPostDetailUseCase.class);
    private final GetMyPostsUseCase getMyPostsUseCase = mock(GetMyPostsUseCase.class);
    private PostController postController;
    private UserInfoController userInfoController;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        postController = new PostController(getPostListUseCase, getPostDetailUseCase,
                mock(CreatePostUseCase.class), mock(UpdatePostUseCase.class), mock(DeletePostUseCase.class),
                mock(LikePostUseCase.class), mock(UnlikePostUseCase.class), mock(StorageUseCase.class),
                mock(ReviewUseCase.class), mock(GetBoardUseCase.class), new RequestGuard(),
                new PostReviewProperties(false, 1, 4, 5, Duration.ofMinutes(1), Duration.ofMinutes(5)),
                mock(CheckPostUploadLimitUseCase.class));
        userInfoController = new UserInfoController(mock(GetMyInfoUseCase.class), getMyPostsUseCase);
    }

    @Test
    void 목록_응답에_post_image_url이_들어가고_post_image는_key_그대로다() {
        Post post = post(PostStatus.DONE, KEY);
        Post noImage = post(PostStatus.DONE, null);
        when(getPostListUseCase.getPosts(3L, null)).thenReturn(List.of(
                new PostSummary(post, 2, false, URL, EquippedItems.empty()),
                new PostSummary(noImage, 0, false, null, EquippedItems.empty())));

        JsonNode posts = json(postController.getPosts(null, 3L)).get("data").get("post");

        assertThat(posts.get(0).get("post_image").asString()).isEqualTo(KEY);
        assertThat(posts.get(0).get("post_image_url").asString()).isEqualTo(URL);
        assertThat(posts.get(0).has("postImageUrl")).isFalse();
        // URL이 없으면 필드 자체가 빠진다
        assertThat(posts.get(1).has("post_image_url")).isFalse();
    }

    @Test
    void 상세_응답에_post_image_url이_들어간다() {
        Post post = post(PostStatus.PENDING, KEY);
        when(getPostDetailUseCase.getPost(any(), any(), anyBoolean())).thenReturn(new PostDetail(post, 0, false, URL));

        JsonNode data = json(postController.getPost(userId, null, UUID.randomUUID())).get("data");

        assertThat(data.get("post_image").asString()).isEqualTo(KEY);
        assertThat(data.get("post_image_url").asString()).isEqualTo(URL);
    }

    @Test
    void 상세에서_URL이_없으면_post_image_url이_빠진다() {
        Post post = post(PostStatus.DONE, KEY);
        when(getPostDetailUseCase.getPost(any(), any(), anyBoolean())).thenReturn(new PostDetail(post, 0, false, null));

        JsonNode data = json(postController.getPost(userId, null, UUID.randomUUID())).get("data");

        assertThat(data.get("post_image").asString()).isEqualTo(KEY);
        assertThat(data.has("post_image_url")).isFalse();
    }

    @Test
    void 내_게시물_응답에_post_image_url이_들어간다() {
        Post post = post(PostStatus.REJECTED, KEY);
        when(getMyPostsUseCase.getMyPosts(userId)).thenReturn(List.of(new MyPostSummary(post, 0, false, URL)));

        JsonNode posts = json(userInfoController.getMyPosts(userId)).get("data").get("post");

        assertThat(posts.get(0).get("post_image").asString()).isEqualTo(KEY);
        assertThat(posts.get(0).get("post_image_url").asString()).isEqualTo(URL);
        assertThat(posts.get(0).get("status").asString()).isEqualTo("REJECTED");
    }

    // ===== helpers =====

    private JsonNode json(Object response) {
        return jsonMapper.readTree(jsonMapper.writeValueAsString(response));
    }

    private Post post(PostStatus status, String postImage) {
        Users author = mock(Users.class);
        lenient().when(author.getUserId()).thenReturn(userId);
        lenient().when(author.getUserName()).thenReturn("작성자");
        Location team = mock(Location.class);
        lenient().when(team.getCityName()).thenReturn("부산광역시");
        lenient().when(team.getFullName()).thenReturn("부산광역시 해운대구");
        Stage stage = mock(Stage.class);
        lenient().when(stage.getSeason()).thenReturn(1);
        lenient().when(stage.getRound()).thenReturn(2);
        Match match = mock(Match.class);
        lenient().when(match.getMission()).thenReturn("귀여운 고양이");
        lenient().when(match.getTeam1()).thenReturn(team);
        lenient().when(match.getTeam2()).thenReturn(team);
        lenient().when(match.getMatchType()).thenReturn(MatchType.NORMAL);
        lenient().when(match.getStage()).thenReturn(stage);
        Board board = mock(Board.class);
        lenient().when(board.getBoardId()).thenReturn(3L);
        lenient().when(board.getMatch()).thenReturn(match);

        Post post = mock(Post.class);
        lenient().when(post.getPostId()).thenReturn(UUID.randomUUID());
        lenient().when(post.getUserId()).thenReturn(author);
        lenient().when(post.getPostImage()).thenReturn(postImage);
        lenient().when(post.getStatus()).thenReturn(status);
        lenient().when(post.getContent()).thenReturn("내용");
        lenient().when(post.getTeamLocation()).thenReturn(team);
        lenient().when(post.getLocation()).thenReturn("해운대구");
        lenient().when(post.getBoard()).thenReturn(board);
        lenient().when(post.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 11, 10, 12, 0));
        return post;
    }
}
