package com.raon.tikitaka.application.post;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.raon.tikitaka.application.block.out.BlockRepositoryPort;
import com.raon.tikitaka.application.equipment.in.GetEquippedItemsUseCase;
import com.raon.tikitaka.application.post.out.PostLikeRepositoryPort;
import com.raon.tikitaka.application.post.out.PostRepositoryPort;
import com.raon.tikitaka.application.ranking.out.RankingRepositoryPort;
import com.raon.tikitaka.application.storage.in.StorageUseCase;
import com.raon.tikitaka.application.territory.in.CheckRankingFrozenUseCase;
import com.raon.tikitaka.domain.post.Post;
import com.raon.tikitaka.domain.post.PostStatus;
import com.raon.tikitaka.domain.user.Users;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 목록·내 게시물·상세에 채우는 이미지 조회 URL(postImageUrl). 값은 StorageUseCase.issueViewUrl이 돌려준 것과 같아야 하고,
 * post_image(S3 key)는 그대로 남아야 한다.
 */
@ExtendWith(MockitoExtension.class)
class PostServiceImageUrlTest {

    @Mock
    private PostRepositoryPort postRepositoryPort;

    @Mock
    private PostLikeRepositoryPort postLikeRepositoryPort;

    @Mock
    private BlockRepositoryPort blockRepositoryPort;

    @Mock
    private StorageUseCase storageUseCase;

    private PostService postService;

    private final UUID authorId = UUID.randomUUID();
    private final UUID otherId = UUID.randomUUID();
    private static final Long BOARD_ID = 3L;

    @BeforeEach
    void setUp() {
        // 조회 경로는 점수 설정을 쓰지 않는다
        postService = new PostService(postRepositoryPort, postLikeRepositoryPort, mock(RankingRepositoryPort.class),
                null, blockRepositoryPort, null, mock(CheckRankingFrozenUseCase.class), storageUseCase,
                mock(GetEquippedItemsUseCase.class));
    }

    // ===== 목록 =====

    @Test
    void 목록의_각_글에_issueViewUrl이_돌려준_URL이_들어가고_key는_그대로다() {
        Post first = post(PostStatus.DONE, "a.jpg");
        Post second = post(PostStatus.DONE, "b.jpg");
        when(postRepositoryPort.findAllActiveByBoardId(BOARD_ID)).thenReturn(List.of(first, second));
        when(storageUseCase.issueViewUrl("a.jpg")).thenReturn(url("https://bucket.s3.amazonaws.com/a.jpg?sig=1"));
        when(storageUseCase.issueViewUrl("b.jpg")).thenReturn(url("https://bucket.s3.amazonaws.com/b.jpg?sig=2"));

        List<PostSummary> result = postService.getPosts(BOARD_ID, null);

        assertThat(result).extracting(PostSummary::postImageUrl).containsExactly(
                "https://bucket.s3.amazonaws.com/a.jpg?sig=1", "https://bucket.s3.amazonaws.com/b.jpg?sig=2");
        assertThat(result).extracting(s -> s.post().getPostImage()).containsExactly("a.jpg", "b.jpg");
        // 글당 한 번만 서명한다
        verify(storageUseCase, times(1)).issueViewUrl("a.jpg");
        verify(storageUseCase, times(1)).issueViewUrl("b.jpg");
    }

    @Test
    void 로그인_상태의_목록도_차단_필터_쿼리를_그대로_쓰고_URL을_채운다() {
        Post post = post(PostStatus.DONE, "a.jpg");
        when(postRepositoryPort.findAllActiveByBoardIdExcludingBlockedBy(BOARD_ID, otherId)).thenReturn(List.of(post));
        when(storageUseCase.issueViewUrl("a.jpg")).thenReturn(url("https://bucket.s3.amazonaws.com/a.jpg?sig=1"));

        List<PostSummary> result = postService.getPosts(BOARD_ID, otherId);

        assertThat(result).extracting(PostSummary::postImageUrl)
                .containsExactly("https://bucket.s3.amazonaws.com/a.jpg?sig=1");
        verify(postRepositoryPort, never()).findAllActiveByBoardId(anyLong());
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://cdn.example.com/a.jpg", "http://cdn.example.com/a.jpg"})
    void 이미_절대_URL이면_서명하지_않고_그대로_쓴다(String absolute) {
        Post post = post(PostStatus.DONE, absolute);
        when(postRepositoryPort.findAllActiveByBoardId(BOARD_ID)).thenReturn(List.of(post));

        List<PostSummary> result = postService.getPosts(BOARD_ID, null);

        assertThat(result.get(0).postImageUrl()).isEqualTo(absolute);
        verify(storageUseCase, never()).issueViewUrl(anyString());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void 이미지가_없으면_URL도_null이다(String postImage) {
        Post post = post(PostStatus.DONE, postImage);
        when(postRepositoryPort.findAllActiveByBoardId(BOARD_ID)).thenReturn(List.of(post));

        List<PostSummary> result = postService.getPosts(BOARD_ID, null);

        assertThat(result.get(0).postImageUrl()).isNull();
        verify(storageUseCase, never()).issueViewUrl(any());
    }

    @Test
    void 한_글의_URL_발급이_실패해도_나머지_글은_정상으로_내려간다() {
        Post broken = post(PostStatus.DONE, "broken.jpg");
        Post ok = post(PostStatus.DONE, "ok.jpg");
        when(postRepositoryPort.findAllActiveByBoardId(BOARD_ID)).thenReturn(List.of(broken, ok));
        when(storageUseCase.issueViewUrl("broken.jpg")).thenThrow(new IllegalStateException("서명 실패"));
        when(storageUseCase.issueViewUrl("ok.jpg")).thenReturn(url("https://bucket.s3.amazonaws.com/ok.jpg?sig=1"));

        List<PostSummary> result = postService.getPosts(BOARD_ID, null);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).post()).isSameAs(broken);
        assertThat(result.get(0).postImageUrl()).isNull();
        assertThat(result.get(1).postImageUrl()).isEqualTo("https://bucket.s3.amazonaws.com/ok.jpg?sig=1");
    }

    @Test
    void URL_발급_실패_로그에는_postId만_남고_파일명이_들어간_key는_남지_않는다() {
        String key = "1b2c3d4e-홍길동_여권사진.jpg";
        Post broken = post(PostStatus.DONE, key);
        when(postRepositoryPort.findAllActiveByBoardId(BOARD_ID)).thenReturn(List.of(broken));
        // 예외 메시지에 key가 들어 있어도 로그로 새지 않아야 한다
        when(storageUseCase.issueViewUrl(key)).thenThrow(new IllegalStateException("서명 실패: " + key));

        Logger logger = (Logger) LoggerFactory.getLogger(PostService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            postService.getPosts(BOARD_ID, null);
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list).hasSize(1);
        String message = appender.list.get(0).getFormattedMessage();
        assertThat(message).contains(broken.getPostId().toString());
        assertThat(message).doesNotContain("홍길동").doesNotContain(key);
    }

    // ===== 내 게시물 =====

    @ParameterizedTest
    @EnumSource(PostStatus.class)
    void 내_게시물은_모든_상태의_글에_URL이_들어간다(PostStatus status) {
        Post post = post(status, "mine.jpg");
        when(postRepositoryPort.findAllActiveByUserId(authorId)).thenReturn(List.of(post));
        when(storageUseCase.issueViewUrl("mine.jpg")).thenReturn(url("https://bucket.s3.amazonaws.com/mine.jpg?sig=1"));

        List<MyPostSummary> result = postService.getMyPosts(authorId);

        assertThat(result).extracting(MyPostSummary::postImageUrl)
                .containsExactly("https://bucket.s3.amazonaws.com/mine.jpg?sig=1");
        assertThat(result.get(0).post().getPostImage()).isEqualTo("mine.jpg");
    }

    // ===== 상세 =====

    @Test
    void 작성자가_보는_심사_중인_글에도_URL이_들어간다() {
        Post post = post(PostStatus.PENDING, "pending.jpg");
        when(postRepositoryPort.findActiveById(post.getPostId())).thenReturn(Optional.of(post));
        when(postRepositoryPort.getUser(authorId)).thenReturn(mock(Users.class));
        when(storageUseCase.issueViewUrl("pending.jpg"))
                .thenReturn(url("https://bucket.s3.amazonaws.com/pending.jpg?sig=1"));

        PostDetail detail = postService.getPost(post.getPostId(), authorId, false);

        assertThat(detail.postImageUrl()).isEqualTo("https://bucket.s3.amazonaws.com/pending.jpg?sig=1");
        assertThat(detail.post().getPostImage()).isEqualTo("pending.jpg");
    }

    @Test
    void 관리자가_보는_심사_실패_글에도_URL이_들어간다() {
        Post post = post(PostStatus.FAILED, "failed.jpg");
        when(postRepositoryPort.findActiveById(post.getPostId())).thenReturn(Optional.of(post));
        when(postRepositoryPort.getUser(otherId)).thenReturn(mock(Users.class));
        when(storageUseCase.issueViewUrl("failed.jpg"))
                .thenReturn(url("https://bucket.s3.amazonaws.com/failed.jpg?sig=1"));

        PostDetail detail = postService.getPost(post.getPostId(), otherId, true);

        assertThat(detail.postImageUrl()).isEqualTo("https://bucket.s3.amazonaws.com/failed.jpg?sig=1");
    }

    @Test
    void 상세_404_규칙은_그대로이고_URL을_발급하지_않는다() {
        Post pending = post(PostStatus.PENDING, "pending.jpg");
        when(postRepositoryPort.findActiveById(pending.getPostId())).thenReturn(Optional.of(pending));

        assertNotFound(() -> postService.getPost(pending.getPostId(), otherId, false));

        Post blocked = post(PostStatus.DONE, "blocked.jpg");
        when(postRepositoryPort.findActiveById(blocked.getPostId())).thenReturn(Optional.of(blocked));
        when(blockRepositoryPort.existsByBlockerAndBlocked(otherId, authorId)).thenReturn(true);

        assertNotFound(() -> postService.getPost(blocked.getPostId(), otherId, false));
        verify(storageUseCase, never()).issueViewUrl(any());
    }

    @Test
    void 상세에서_URL_발급이_실패하면_필드만_비우고_응답한다() {
        Post post = post(PostStatus.DONE, "broken.jpg");
        when(postRepositoryPort.findActiveById(post.getPostId())).thenReturn(Optional.of(post));
        when(storageUseCase.issueViewUrl("broken.jpg")).thenThrow(new IllegalStateException("서명 실패"));

        PostDetail detail = postService.getPost(post.getPostId(), null, false);

        assertThat(detail.post()).isSameAs(post);
        assertThat(detail.postImageUrl()).isNull();
    }

    // ===== helpers =====

    private Post post(PostStatus status, String postImage) {
        Users author = mock(Users.class);
        lenient().when(author.getUserId()).thenReturn(authorId);
        Post post = mock(Post.class);
        lenient().when(post.getPostId()).thenReturn(UUID.randomUUID());
        lenient().when(post.getUserId()).thenReturn(author);
        lenient().when(post.getStatus()).thenReturn(status);
        lenient().when(post.isActive()).thenReturn(true);
        lenient().when(post.isDone()).thenReturn(status == PostStatus.DONE);
        lenient().when(post.getPostImage()).thenReturn(postImage);
        return post;
    }

    private static URL url(String value) {
        try {
            return URI.create(value).toURL();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static void assertNotFound(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getReason()).isEqualTo("게시물을 찾을 수 없습니다.");
                });
    }
}
