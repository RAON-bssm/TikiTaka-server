package com.raon.tikitaka.application.post;

import com.raon.tikitaka.application.block.out.BlockRepositoryPort;
import com.raon.tikitaka.application.post.out.PostLikeRepositoryPort;
import com.raon.tikitaka.application.post.out.PostRepositoryPort;
import com.raon.tikitaka.application.ranking.out.RankingRepositoryPort;
import com.raon.tikitaka.domain.post.Post;
import com.raon.tikitaka.domain.user.Users;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 게시물 목록·상세의 차단 필터 분기. 실제 필터 조건은 쿼리에 있으므로 여기서는 어떤 쿼리를 고르는지만 본다.
 */
@ExtendWith(MockitoExtension.class)
class PostServiceBlockFilterTest {

    @Mock
    private PostRepositoryPort postRepositoryPort;

    @Mock
    private PostLikeRepositoryPort postLikeRepositoryPort;

    @Mock
    private RankingRepositoryPort rankingRepositoryPort;

    @Mock
    private BlockRepositoryPort blockRepositoryPort;

    private PostService postService;

    private final UUID viewerId = UUID.randomUUID();
    private final UUID authorId = UUID.randomUUID();
    private final UUID postId = UUID.randomUUID();
    private static final Long BOARD_ID = 3L;

    @BeforeEach
    void setUp() {
        // 조회 경로는 점수 설정을 쓰지 않는다
        postService = new PostService(postRepositoryPort, postLikeRepositoryPort, rankingRepositoryPort, null,
                blockRepositoryPort, null);
    }

    // ===== 목록 =====

    @Test
    void 로그인_상태면_차단_필터_쿼리로_목록을_읽는다() {
        Post post = post();
        when(postRepositoryPort.findAllActiveByBoardIdExcludingBlockedBy(BOARD_ID, viewerId)).thenReturn(List.of(post));

        List<PostSummary> result = postService.getPosts(BOARD_ID, viewerId);

        assertThat(result).extracting(PostSummary::post).containsExactly(post);
        verify(postRepositoryPort, never()).findAllActiveByBoardId(anyLong());
    }

    @Test
    void 비로그인이면_기존_쿼리로_목록을_읽는다() {
        Post post = post();
        when(postRepositoryPort.findAllActiveByBoardId(BOARD_ID)).thenReturn(List.of(post));

        List<PostSummary> result = postService.getPosts(BOARD_ID, null);

        assertThat(result).extracting(PostSummary::post).containsExactly(post);
        verify(postRepositoryPort, never()).findAllActiveByBoardIdExcludingBlockedBy(any(), any());
    }

    // ===== 상세 =====

    @Test
    void 차단한_작성자의_게시물_상세는_404() {
        Post post = post();
        when(postRepositoryPort.findActiveById(postId)).thenReturn(Optional.of(post));
        when(blockRepositoryPort.existsByBlockerAndBlocked(viewerId, authorId)).thenReturn(true);

        assertThatThrownBy(() -> postService.getPost(postId, viewerId, false))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException ex = (ResponseStatusException) e;
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getReason()).isEqualTo("게시물을 찾을 수 없습니다.");
                });
    }

    @Test
    void 탈퇴한_작성자라_조회_쿼리가_빈_결과를_주면_상세는_404() {
        // 탈퇴 작성자 제외는 findActiveById 쿼리에 있다(WithdrawnUserQueryTest). 여기서는 빈 결과가 404가 되는지만 본다
        when(postRepositoryPort.findActiveById(postId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.getPost(postId, viewerId, false))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException ex = (ResponseStatusException) e;
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getReason()).isEqualTo("게시물을 찾을 수 없습니다.");
                });
    }

    @Test
    void 차단하지_않은_작성자의_게시물_상세는_그대로_보인다() {
        Post post = post();
        when(postRepositoryPort.findActiveById(postId)).thenReturn(Optional.of(post));
        when(blockRepositoryPort.existsByBlockerAndBlocked(viewerId, authorId)).thenReturn(false);
        Users viewer = mock(Users.class);
        when(postRepositoryPort.getUser(viewerId)).thenReturn(viewer);

        PostDetail detail = postService.getPost(postId, viewerId, false);

        assertThat(detail.post()).isSameAs(post);
    }

    @Test
    void 비로그인이면_차단_여부를_보지_않고_상세를_보여준다() {
        Post post = post();
        when(postRepositoryPort.findActiveById(postId)).thenReturn(Optional.of(post));

        PostDetail detail = postService.getPost(postId, null, false);

        assertThat(detail.post()).isSameAs(post);
        verify(blockRepositoryPort, never()).existsByBlockerAndBlocked(any(), any());
    }

    // ===== helpers =====

    private Post post() {
        Users author = mock(Users.class);
        lenient().when(author.getUserId()).thenReturn(authorId);
        Post post = mock(Post.class);
        lenient().when(post.getPostId()).thenReturn(postId);
        lenient().when(post.getUserId()).thenReturn(author);
        lenient().when(post.isDone()).thenReturn(true);
        return post;
    }
}
