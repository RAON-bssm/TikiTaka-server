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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.net.MalformedURLException;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 글 목록 커서 페이징. size + 1개를 읽어 다음 페이지를 판단하고, 여분 한 건은 응답과 뒤따르는 조회
 * (좋아요, 이미지 URL, 착용 정보)에 섞이지 않아야 한다. 뒤따르는 조회 횟수는 페이지 크기와 상관없이 같아야 한다.
 */
@ExtendWith(MockitoExtension.class)
class PostServicePagingTest {

    @Mock
    private PostRepositoryPort postRepositoryPort;

    @Mock
    private PostLikeRepositoryPort postLikeRepositoryPort;

    @Mock
    private StorageUseCase storageUseCase;

    @Mock
    private GetEquippedItemsUseCase getEquippedItemsUseCase;

    private PostService postService;

    private static final Long BOARD_ID = 3L;
    private final UUID viewerId = UUID.randomUUID();

    @BeforeEach
    void setUp() throws MalformedURLException {
        // 조회 경로는 점수 설정을 쓰지 않는다
        postService = new PostService(postRepositoryPort, postLikeRepositoryPort, mock(RankingRepositoryPort.class),
                null, mock(BlockRepositoryPort.class), null, mock(CheckRankingFrozenUseCase.class), storageUseCase,
                getEquippedItemsUseCase);
        lenient().when(storageUseCase.issueViewUrl(anyString()))
                .thenReturn(URI.create("https://bucket.s3.amazonaws.com/x.jpg?sig=1").toURL());
    }

    // ===== 다음 페이지 판단 =====

    @Test
    void size_더하기_1개가_오면_size개만_내리고_마지막_글_기준_next_cursor를_준다() {
        List<Post> fetched = posts(4);
        when(postRepositoryPort.findActivePageByBoardIdExcludingBlockedBy(BOARD_ID, viewerId, null, 4)).thenReturn(fetched);

        PostPage page = postService.getPostPage(BOARD_ID, viewerId, 3, null);

        assertThat(page.posts()).extracting(PostSummary::post).containsExactlyElementsOf(fetched.subList(0, 3));
        assertThat(page.nextCursor()).isEqualTo(PostCursor.of(fetched.get(2)).encode());
    }

    @Test
    void 정확히_size개가_남으면_next_cursor가_없다() {
        List<Post> fetched = posts(3);
        when(postRepositoryPort.findActivePageByBoardIdExcludingBlockedBy(BOARD_ID, viewerId, null, 4)).thenReturn(fetched);

        PostPage page = postService.getPostPage(BOARD_ID, viewerId, 3, null);

        assertThat(page.posts()).hasSize(3);
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void 빈_페이지는_빈_목록이고_next_cursor가_없다() {
        when(postRepositoryPort.findActivePageByBoardIdExcludingBlockedBy(BOARD_ID, viewerId, null, 4)).thenReturn(List.of());

        PostPage page = postService.getPostPage(BOARD_ID, viewerId, 3, null);

        assertThat(page.posts()).isEmpty();
        assertThat(page.nextCursor()).isNull();
    }

    // ===== 여분 한 건 =====

    @Test
    void 여분_한_건은_좋아요_이미지_착용_조회_대상에서_빠진다() {
        List<Post> fetched = posts(4);
        Post extra = fetched.get(3);
        when(postRepositoryPort.findActivePageByBoardIdExcludingBlockedBy(BOARD_ID, viewerId, null, 4)).thenReturn(fetched);

        postService.getPostPage(BOARD_ID, viewerId, 3, null);

        List<UUID> pageIds = fetched.subList(0, 3).stream().map(Post::getPostId).toList();
        verify(postLikeRepositoryPort).countByPostIds(pageIds);
        verify(postLikeRepositoryPort).findLikedPostIds(pageIds, viewerId);
        verify(storageUseCase, never()).issueViewUrl(extra.getPostImage());
        assertThat(captureEquipmentIds()).doesNotContain(extra.getUserId().getUserId()).hasSize(3);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3, 10, 50})
    void 페이지_크기와_상관없이_좋아요와_착용_조회는_한_번씩이다(int size) {
        List<Post> fetched = posts(size + 1);
        when(postRepositoryPort.findActivePageByBoardIdExcludingBlockedBy(BOARD_ID, viewerId, null, size + 1))
                .thenReturn(fetched);

        postService.getPostPage(BOARD_ID, viewerId, size, null);

        verify(postRepositoryPort, times(1)).findActivePageByBoardIdExcludingBlockedBy(anyLong(), any(), any(), anyInt());
        verify(postLikeRepositoryPort, times(1)).countByPostIds(any());
        verify(postLikeRepositoryPort, times(1)).findLikedPostIds(any(), any());
        verify(getEquippedItemsUseCase, times(1)).getEquippedItemsByUserIds(any());
        // 이미지 URL은 네트워크 없는 로컬 서명이라 글마다 한 번씩이고 여분 글은 서명하지 않는다
        verify(storageUseCase, times(size)).issueViewUrl(anyString());
    }

    // ===== 쿼리 선택과 커서 =====

    @Test
    void 비로그인은_차단_필터_없는_쿼리를_쓰고_좋아요_여부는_조회하지_않는다() {
        List<Post> fetched = posts(2);
        when(postRepositoryPort.findActivePageByBoardId(BOARD_ID, null, 4)).thenReturn(fetched);

        PostPage page = postService.getPostPage(BOARD_ID, null, 3, null);

        assertThat(page.posts()).hasSize(2);
        verify(postRepositoryPort, never()).findActivePageByBoardIdExcludingBlockedBy(anyLong(), any(), any(), anyInt());
        verify(postLikeRepositoryPort, never()).findLikedPostIds(any(), any());
    }

    @Test
    void size가_없으면_기본_20이라_21개를_읽는다() {
        postService.getPostPage(BOARD_ID, viewerId, null, null);

        verify(postRepositoryPort).findActivePageByBoardIdExcludingBlockedBy(BOARD_ID, viewerId, null, 21);
    }

    @Test
    void cursor를_해석해_포트에_넘긴다() {
        PostCursor cursor = new PostCursor(LocalDateTime.of(2026, 11, 10, 10, 0, 0, 1_000), UUID.randomUUID());

        postService.getPostPage(BOARD_ID, viewerId, 3, cursor.encode());

        verify(postRepositoryPort).findActivePageByBoardIdExcludingBlockedBy(BOARD_ID, viewerId, cursor, 4);
    }

    @Test
    void 빈_cursor는_첫_페이지다() {
        postService.getPostPage(BOARD_ID, viewerId, 3, "");

        verify(postRepositoryPort).findActivePageByBoardIdExcludingBlockedBy(eq(BOARD_ID), eq(viewerId), isNull(), eq(4));
    }

    // ===== 400 =====

    @ParameterizedTest
    @ValueSource(ints = {0, 51, -1, Integer.MIN_VALUE})
    void size가_범위를_벗어나면_조회하지_않고_400(int size) {
        assertThatThrownBy(() -> postService.getPostPage(BOARD_ID, viewerId, size, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getReason()).isEqualTo("size는 1 이상 50 이하여야 합니다.");
                });
        verifyNoInteractions(postRepositoryPort);
    }

    @Test
    void 잘못된_cursor는_조회하지_않고_400() {
        assertThatThrownBy(() -> postService.getPostPage(BOARD_ID, viewerId, 3, "!!bad"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getReason()).isEqualTo("잘못된 cursor입니다.");
                });
        verifyNoInteractions(postRepositoryPort);
    }

    // ===== helpers =====

    @SuppressWarnings("unchecked")
    private Collection<UUID> captureEquipmentIds() {
        ArgumentCaptor<Collection<UUID>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(getEquippedItemsUseCase).getEquippedItemsByUserIds(captor.capture());
        return captor.getValue();
    }

    /** 최신순으로 정렬된 것처럼 시각을 1초씩 줄여 만든다. 작성자는 글마다 다르다 */
    private static List<Post> posts(int count) {
        List<Post> posts = new ArrayList<>();
        LocalDateTime base = LocalDateTime.of(2026, 11, 10, 10, 0, 0, 123_456_000);
        for (int i = 0; i < count; i++) {
            Users author = mock(Users.class);
            lenient().when(author.getUserId()).thenReturn(UUID.randomUUID());
            Post post = mock(Post.class);
            lenient().when(post.getPostId()).thenReturn(UUID.randomUUID());
            lenient().when(post.getUserId()).thenReturn(author);
            lenient().when(post.getCreatedAt()).thenReturn(base.minusSeconds(i));
            lenient().when(post.getPostImage()).thenReturn("key-" + i + ".jpg");
            posts.add(post);
        }
        return posts;
    }
}
