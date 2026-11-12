package com.raon.tikitaka.application.post;

import com.raon.tikitaka.application.block.out.BlockRepositoryPort;
import com.raon.tikitaka.application.equipment.in.GetEquippedItemsUseCase;
import com.raon.tikitaka.application.post.out.PostLikeRepositoryPort;
import com.raon.tikitaka.application.post.out.PostRepositoryPort;
import com.raon.tikitaka.application.ranking.out.RankingRepositoryPort;
import com.raon.tikitaka.application.storage.in.StorageUseCase;
import com.raon.tikitaka.application.territory.in.CheckRankingFrozenUseCase;
import com.raon.tikitaka.domain.post.Post;
import com.raon.tikitaka.domain.user.Users;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 글 목록의 썸네일(postThumbnail, postThumbnailUrl). 썸네일이 있으면 썸네일을, 없거나 URL 발급에 실패하면 원본을 쓴다.
 * 원본(postImage, postImageUrl)은 썸네일과 상관없이 그대로다.
 */
@ExtendWith(MockitoExtension.class)
class PostServiceThumbnailTest {

    private static final Long BOARD_ID = 3L;
    private static final String ORIGINAL_KEY = "1b2c-photo.jpg";
    private static final String THUMBNAIL_KEY = "thumbnails/9f8e.jpg";
    private static final String ORIGINAL_URL = "https://bucket.s3.amazonaws.com/1b2c-photo.jpg?sig=1";
    private static final String THUMBNAIL_URL = "https://bucket.s3.amazonaws.com/thumbnails/9f8e.jpg?sig=2";

    @Mock
    private PostRepositoryPort postRepositoryPort;

    @Mock
    private StorageUseCase storageUseCase;

    private PostService postService;

    @BeforeEach
    void setUp() {
        // 조회 경로는 점수 설정을 쓰지 않는다
        postService = new PostService(postRepositoryPort, mock(PostLikeRepositoryPort.class),
                mock(RankingRepositoryPort.class), null, mock(BlockRepositoryPort.class), null,
                mock(CheckRankingFrozenUseCase.class), storageUseCase, mock(GetEquippedItemsUseCase.class));
    }

    @Test
    void 썸네일이_있으면_썸네일_key와_URL이_들어가고_원본은_그대로다() {
        Post post = post(ORIGINAL_KEY, THUMBNAIL_KEY);
        when(postRepositoryPort.findAllActiveByBoardId(BOARD_ID)).thenReturn(List.of(post));
        when(storageUseCase.issueViewUrl(ORIGINAL_KEY)).thenReturn(url(ORIGINAL_URL));
        when(storageUseCase.issueViewUrl(THUMBNAIL_KEY)).thenReturn(url(THUMBNAIL_URL));

        PostSummary summary = postService.getPosts(BOARD_ID, null).get(0);

        assertThat(summary.postThumbnail()).isEqualTo(THUMBNAIL_KEY);
        assertThat(summary.postThumbnailUrl()).isEqualTo(THUMBNAIL_URL);
        assertThat(summary.postImageUrl()).isEqualTo(ORIGINAL_URL);
        assertThat(summary.post().getPostImage()).isEqualTo(ORIGINAL_KEY);
    }

    @Test
    void 썸네일이_없으면_원본_key와_URL을_쓰고_서명은_한_번만_한다() {
        Post post = post(ORIGINAL_KEY, null);
        when(postRepositoryPort.findAllActiveByBoardId(BOARD_ID)).thenReturn(List.of(post));
        when(storageUseCase.issueViewUrl(ORIGINAL_KEY)).thenReturn(url(ORIGINAL_URL));

        PostSummary summary = postService.getPosts(BOARD_ID, null).get(0);

        assertThat(summary.postThumbnail()).isEqualTo(ORIGINAL_KEY);
        assertThat(summary.postThumbnailUrl()).isEqualTo(ORIGINAL_URL);
        verify(storageUseCase, times(1)).issueViewUrl(any());
    }

    @Test
    void 썸네일_URL_발급에_실패하면_원본으로_대신한다() {
        Post post = post(ORIGINAL_KEY, THUMBNAIL_KEY);
        when(postRepositoryPort.findAllActiveByBoardId(BOARD_ID)).thenReturn(List.of(post));
        when(storageUseCase.issueViewUrl(ORIGINAL_KEY)).thenReturn(url(ORIGINAL_URL));
        when(storageUseCase.issueViewUrl(THUMBNAIL_KEY)).thenThrow(new IllegalStateException("서명 실패"));

        PostSummary summary = postService.getPosts(BOARD_ID, null).get(0);

        assertThat(summary.postThumbnail()).isEqualTo(ORIGINAL_KEY);
        assertThat(summary.postThumbnailUrl()).isEqualTo(ORIGINAL_URL);
    }

    @Test
    void 이미지가_없으면_썸네일_key와_URL도_null이다() {
        Post post = post(null, null);
        when(postRepositoryPort.findAllActiveByBoardId(BOARD_ID)).thenReturn(List.of(post));

        PostSummary summary = postService.getPosts(BOARD_ID, null).get(0);

        assertThat(summary.postThumbnail()).isNull();
        assertThat(summary.postThumbnailUrl()).isNull();
        verify(storageUseCase, never()).issueViewUrl(any());
    }

    @Test
    void 원본_URL도_발급에_실패하면_썸네일_key와_URL이_둘_다_null이다() {
        Post post = post(ORIGINAL_KEY, null);
        when(postRepositoryPort.findAllActiveByBoardId(BOARD_ID)).thenReturn(List.of(post));
        when(storageUseCase.issueViewUrl(ORIGINAL_KEY)).thenThrow(new IllegalStateException("서명 실패"));

        PostSummary summary = postService.getPosts(BOARD_ID, null).get(0);

        assertThat(summary.postThumbnail()).isNull();
        assertThat(summary.postThumbnailUrl()).isNull();
    }

    @Test
    void 페이지_조회도_같은_규칙으로_썸네일을_채운다() {
        Post post = post(ORIGINAL_KEY, THUMBNAIL_KEY);
        when(postRepositoryPort.findActivePageByBoardId(BOARD_ID, null, 21)).thenReturn(List.of(post));
        when(storageUseCase.issueViewUrl(ORIGINAL_KEY)).thenReturn(url(ORIGINAL_URL));
        when(storageUseCase.issueViewUrl(THUMBNAIL_KEY)).thenReturn(url(THUMBNAIL_URL));

        PostSummary summary = postService.getPostPage(BOARD_ID, null, null, null).posts().get(0);

        assertThat(summary.postThumbnail()).isEqualTo(THUMBNAIL_KEY);
        assertThat(summary.postThumbnailUrl()).isEqualTo(THUMBNAIL_URL);
    }

    // ===== helpers =====

    private Post post(String postImage, String postThumbnail) {
        Users author = mock(Users.class);
        lenient().when(author.getUserId()).thenReturn(UUID.randomUUID());
        Post post = mock(Post.class);
        lenient().when(post.getPostId()).thenReturn(UUID.randomUUID());
        lenient().when(post.getUserId()).thenReturn(author);
        lenient().when(post.getPostImage()).thenReturn(postImage);
        lenient().when(post.getPostThumbnail()).thenReturn(postThumbnail);
        return post;
    }

    private static URL url(String value) {
        try {
            return URI.create(value).toURL();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
