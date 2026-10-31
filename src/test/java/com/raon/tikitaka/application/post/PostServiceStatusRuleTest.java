package com.raon.tikitaka.application.post;

import com.raon.tikitaka.application.block.out.BlockRepositoryPort;
import com.raon.tikitaka.application.post.out.PostLikeRepositoryPort;
import com.raon.tikitaka.application.post.out.PostRepositoryPort;
import com.raon.tikitaka.application.ranking.out.RankingRepositoryPort;
import com.raon.tikitaka.application.territory.in.CheckRankingFrozenUseCase;
import com.raon.tikitaka.domain.post.Post;
import com.raon.tikitaka.domain.post.PostStatus;
import com.raon.tikitaka.domain.user.Users;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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
 * 게시물 심사 상태에 따른 노출·조작 규칙. 피드의 상태 필터는 쿼리에 있으므로 여기서는
 * findActiveById로 읽은 뒤 서비스가 상태를 보고 막는 경로(상세, 좋아요, 수정, 삭제)만 본다.
 */
@ExtendWith(MockitoExtension.class)
class PostServiceStatusRuleTest {

    @Mock
    private PostRepositoryPort postRepositoryPort;

    @Mock
    private PostLikeRepositoryPort postLikeRepositoryPort;

    @Mock
    private RankingRepositoryPort rankingRepositoryPort;

    @Mock
    private BlockRepositoryPort blockRepositoryPort;

    private PostService postService;

    private final UUID authorId = UUID.randomUUID();
    private final UUID otherId = UUID.randomUUID();
    private final UUID postId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // 조회·수정·삭제 경로는 점수 설정을 쓰지 않는다(삭제는 점수가 없는 글만 다룬다)
        postService = new PostService(postRepositoryPort, postLikeRepositoryPort, rankingRepositoryPort, null,
                blockRepositoryPort, null, mock(CheckRankingFrozenUseCase.class));
    }

    // ===== 상세 =====

    @ParameterizedTest
    @EnumSource(value = PostStatus.class, names = "DONE", mode = EnumSource.Mode.EXCLUDE)
    void 심사가_끝나지_않은_글을_다른_사람이_보면_404(PostStatus status) {
        Post post = post(status);
        when(postRepositoryPort.findActiveById(postId)).thenReturn(Optional.of(post));

        assertNotFound(() -> postService.getPost(postId, otherId, false));
    }

    @Test
    void 심사가_끝나지_않은_글은_비로그인이면_404() {
        Post post = post(PostStatus.PENDING);
        when(postRepositoryPort.findActiveById(postId)).thenReturn(Optional.of(post));

        assertNotFound(() -> postService.getPost(postId, null, false));
    }

    @ParameterizedTest
    @EnumSource(PostStatus.class)
    void 작성자_본인은_모든_상태의_글을_본다(PostStatus status) {
        Post post = post(status);
        when(postRepositoryPort.findActiveById(postId)).thenReturn(Optional.of(post));
        when(postRepositoryPort.getUser(authorId)).thenReturn(mock(Users.class));

        PostDetail detail = postService.getPost(postId, authorId, false);

        assertThat(detail.post()).isSameAs(post);
    }

    @Test
    void 관리자는_심사가_끝나지_않은_글도_본다() {
        Post post = post(PostStatus.FAILED);
        when(postRepositoryPort.findActiveById(postId)).thenReturn(Optional.of(post));
        when(postRepositoryPort.getUser(otherId)).thenReturn(mock(Users.class));

        PostDetail detail = postService.getPost(postId, otherId, true);

        assertThat(detail.post()).isSameAs(post);
    }

    // ===== 좋아요 =====

    @ParameterizedTest
    @EnumSource(value = PostStatus.class, names = "DONE", mode = EnumSource.Mode.EXCLUDE)
    void 심사가_끝나지_않은_글은_좋아요와_취소가_404(PostStatus status) {
        Post post = post(status);
        when(postRepositoryPort.findActiveById(postId)).thenReturn(Optional.of(post));

        assertNotFound(() -> postService.like(postId, otherId));
        assertNotFound(() -> postService.unlike(postId, otherId));
        verify(postLikeRepositoryPort, never()).save(any());
        verify(postLikeRepositoryPort, never()).deleteByPostAndUser(any(), any());
    }

    @Test
    void DONE_글은_좋아요가_저장된다() {
        Post post = post(PostStatus.DONE);
        Users user = mock(Users.class);
        when(postRepositoryPort.findActiveById(postId)).thenReturn(Optional.of(post));
        when(postRepositoryPort.getUser(otherId)).thenReturn(user);
        when(postLikeRepositoryPort.existsByPostAndUser(post, user)).thenReturn(false);

        postService.like(postId, otherId);

        verify(postLikeRepositoryPort).save(any());
    }

    // ===== 수정 =====

    @Test
    void 심사_중인_글은_수정하면_409() {
        Post post = post(PostStatus.PENDING);
        when(postRepositoryPort.findActiveById(postId)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.updatePost(postId, authorId, false, "새 내용"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getReason()).isEqualTo("심사 중에는 수정할 수 없어요");
                });
        verify(post, never()).updateContent(any());
    }

    @ParameterizedTest
    @EnumSource(value = PostStatus.class, names = "PENDING", mode = EnumSource.Mode.EXCLUDE)
    void 심사_중이_아니면_수정할_수_있다(PostStatus status) {
        Post post = post(status);
        when(postRepositoryPort.findActiveById(postId)).thenReturn(Optional.of(post));

        postService.updatePost(postId, authorId, false, "새 내용");

        verify(post).updateContent("새 내용");
    }

    // ===== 삭제 =====

    @ParameterizedTest
    @EnumSource(value = PostStatus.class, names = "DONE", mode = EnumSource.Mode.EXCLUDE)
    void 심사가_끝나지_않은_글도_삭제할_수_있고_점수는_건드리지_않는다(PostStatus status) {
        Post post = post(status);
        when(postRepositoryPort.findByIdForUpdate(postId)).thenReturn(Optional.of(post));

        postService.deletePost(postId, authorId, false);

        verify(post).deactivate();
        verify(rankingRepositoryPort, never()).addUserScore(anyLong(), any(), anyLong());
        verify(rankingRepositoryPort, never()).addLocationScore(anyLong(), anyLong(), anyLong());
    }

    // ===== helpers =====

    /** DONE이 아닌 글은 score가 없다. 실제 엔티티와 같게 점수는 비워둔다 */
    private Post post(PostStatus status) {
        Users author = mock(Users.class);
        lenient().when(author.getUserId()).thenReturn(authorId);
        Post post = mock(Post.class);
        lenient().when(post.getPostId()).thenReturn(postId);
        lenient().when(post.getUserId()).thenReturn(author);
        lenient().when(post.getStatus()).thenReturn(status);
        lenient().when(post.isActive()).thenReturn(true);
        lenient().when(post.isDone()).thenReturn(status == PostStatus.DONE);
        lenient().when(post.isPending()).thenReturn(status == PostStatus.PENDING);
        return post;
    }

    private static void assertNotFound(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getReason()).isEqualTo("게시물을 찾을 수 없습니다.");
                });
    }
}
