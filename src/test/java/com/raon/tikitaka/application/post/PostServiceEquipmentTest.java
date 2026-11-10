package com.raon.tikitaka.application.post;

import com.raon.tikitaka.application.block.out.BlockRepositoryPort;
import com.raon.tikitaka.application.equipment.EquippedItems;
import com.raon.tikitaka.application.equipment.in.GetEquippedItemsUseCase;
import com.raon.tikitaka.application.post.out.PostLikeRepositoryPort;
import com.raon.tikitaka.application.post.out.PostRepositoryPort;
import com.raon.tikitaka.application.ranking.out.RankingRepositoryPort;
import com.raon.tikitaka.application.storage.in.StorageUseCase;
import com.raon.tikitaka.application.territory.in.CheckRankingFrozenUseCase;
import com.raon.tikitaka.domain.post.Post;
import com.raon.tikitaka.domain.post.PostStatus;
import com.raon.tikitaka.domain.product.Product;
import com.raon.tikitaka.domain.user.Users;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 글 목록에 작성자 착용 정보를 붙이는 규칙. 작성자 id를 중복 없이 모아 한 번만 조회하고,
 * 착용한 것이 없는 작성자도 빈 EquippedItems가 붙는지 본다. 피드 쿼리 선택(DONE만, 차단 제외)은 그대로여야 한다.
 */
@ExtendWith(MockitoExtension.class)
class PostServiceEquipmentTest {

    @Mock
    private PostRepositoryPort postRepositoryPort;

    @Mock
    private PostLikeRepositoryPort postLikeRepositoryPort;

    @Mock
    private BlockRepositoryPort blockRepositoryPort;

    @Mock
    private GetEquippedItemsUseCase getEquippedItemsUseCase;

    private PostService postService;

    private static final Long BOARD_ID = 3L;

    @BeforeEach
    void setUp() {
        // 조회 경로는 점수 설정을 쓰지 않는다
        postService = new PostService(postRepositoryPort, postLikeRepositoryPort, mock(RankingRepositoryPort.class),
                null, blockRepositoryPort, null, mock(CheckRankingFrozenUseCase.class), mock(StorageUseCase.class),
                getEquippedItemsUseCase);
    }

    @Test
    void 글_10개_작성자_10명이면_작성자_10명을_한_번에_조회한다() {
        List<Post> posts = new ArrayList<>();
        List<UUID> authorIds = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            UUID authorId = UUID.randomUUID();
            authorIds.add(authorId);
            posts.add(post(authorId));
        }
        when(postRepositoryPort.findAllActiveByBoardId(BOARD_ID)).thenReturn(posts);

        postService.getPosts(BOARD_ID, null);

        Collection<UUID> requested = captureRequestedIds();
        assertThat(requested).containsExactlyInAnyOrderElementsOf(authorIds);
    }

    @Test
    void 같은_작성자의_글이_여러_개여도_id는_한_번만_조회한다() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        List<Post> posts = List.of(post(a), post(b), post(a), post(a), post(b));
        when(postRepositoryPort.findAllActiveByBoardId(BOARD_ID)).thenReturn(posts);

        postService.getPosts(BOARD_ID, null);

        Collection<UUID> requested = captureRequestedIds();
        assertThat(requested).hasSize(2).containsExactlyInAnyOrder(a, b);
    }

    @Test
    void 작성자별_착용_정보가_해당_글에_붙고_착용이_없는_작성자는_빈_값이다() {
        UUID dressed = UUID.randomUUID();
        UUID plain = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        EquippedItems dressedItems = new EquippedItems(List.of(mock(Product.class)), Map.of());
        Post dressedPost = post(dressed);
        Post plainPost = post(plain);
        Post missingPost = post(missing);
        when(postRepositoryPort.findAllActiveByBoardId(BOARD_ID)).thenReturn(List.of(dressedPost, plainPost, missingPost));
        // missing은 데이터 불일치로 맵에 없는 경우다. 그 글만 빈 값으로 내려가야 한다
        when(getEquippedItemsUseCase.getEquippedItemsByUserIds(any()))
                .thenReturn(Map.of(dressed, dressedItems, plain, EquippedItems.empty()));

        List<PostSummary> result = postService.getPosts(BOARD_ID, null);

        assertThat(result).extracting(PostSummary::post).containsExactly(dressedPost, plainPost, missingPost);
        assertThat(result.get(0).authorEquipment()).isSameAs(dressedItems);
        assertThat(result.get(1).authorEquipment().products()).isEmpty();
        assertThat(result.get(1).authorEquipment().colors()).isEmpty();
        assertThat(result.get(2).authorEquipment()).isNotNull();
        assertThat(result.get(2).authorEquipment().products()).isEmpty();
    }

    @Test
    void 로그인_상태면_차단_필터_쿼리를_그대로_쓰고_착용_정보도_붙인다() {
        UUID viewerId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        List<Post> posts = List.of(post(authorId));
        when(postRepositoryPort.findAllActiveByBoardIdExcludingBlockedBy(BOARD_ID, viewerId)).thenReturn(posts);

        List<PostSummary> result = postService.getPosts(BOARD_ID, viewerId);

        assertThat(result).hasSize(1);
        assertThat(captureRequestedIds()).containsExactly(authorId);
        verify(postRepositoryPort, never()).findAllActiveByBoardId(anyLong());
    }

    @Test
    void 내_게시물과_상세는_착용_정보를_조회하지_않는다() {
        UUID authorId = UUID.randomUUID();
        Post post = post(authorId);
        when(postRepositoryPort.findAllActiveByUserId(authorId)).thenReturn(List.of(post));
        when(postRepositoryPort.findActiveById(post.getPostId())).thenReturn(Optional.of(post));

        postService.getMyPosts(authorId);
        postService.getPost(post.getPostId(), null, false);

        verify(getEquippedItemsUseCase, never()).getEquippedItemsByUserIds(any());
    }

    // ===== helpers =====

    @SuppressWarnings("unchecked")
    private Collection<UUID> captureRequestedIds() {
        ArgumentCaptor<Collection<UUID>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(getEquippedItemsUseCase, times(1)).getEquippedItemsByUserIds(captor.capture());
        return captor.getValue();
    }

    private Post post(UUID authorId) {
        Users author = mock(Users.class);
        lenient().when(author.getUserId()).thenReturn(authorId);
        Post post = mock(Post.class);
        lenient().when(post.getPostId()).thenReturn(UUID.randomUUID());
        lenient().when(post.getUserId()).thenReturn(author);
        lenient().when(post.getStatus()).thenReturn(PostStatus.DONE);
        lenient().when(post.isDone()).thenReturn(true);
        return post;
    }
}
