package com.raon.tikitaka.adapter.post;

import com.raon.tikitaka.application.board.in.GetBoardUseCase;
import com.raon.tikitaka.application.post.in.CreatePostUseCase;
import com.raon.tikitaka.application.post.in.DeletePostUseCase;
import com.raon.tikitaka.application.post.in.GetPostDetailUseCase;
import com.raon.tikitaka.application.post.in.GetPostListUseCase;
import com.raon.tikitaka.application.post.in.LikePostUseCase;
import com.raon.tikitaka.application.post.in.UnlikePostUseCase;
import com.raon.tikitaka.application.post.in.UpdatePostUseCase;
import com.raon.tikitaka.application.review.in.ReviewUseCase;
import com.raon.tikitaka.application.storage.in.StorageUseCase;
import com.raon.tikitaka.global.guard.RequestGuard;
import com.raon.tikitaka.global.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 컨트롤러가 유스케이스 호출을 가드로 감싸는지 확인한다. 유스케이스는 mock이고 가드는 실제 객체다.
 */
class PostControllerGuardTest {

    private final DeletePostUseCase deletePostUseCase = mock(DeletePostUseCase.class);
    private final LikePostUseCase likePostUseCase = mock(LikePostUseCase.class);
    private final UnlikePostUseCase unlikePostUseCase = mock(UnlikePostUseCase.class);
    private PostController controller;

    private final UUID userId = UUID.randomUUID();
    private final UUID postId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        controller = new PostController(mock(GetPostListUseCase.class), mock(GetPostDetailUseCase.class),
                mock(CreatePostUseCase.class), mock(UpdatePostUseCase.class), deletePostUseCase,
                likePostUseCase, unlikePostUseCase, mock(StorageUseCase.class), mock(ReviewUseCase.class),
                mock(GetBoardUseCase.class), new RequestGuard());
    }

    @Test
    void 게시물_삭제가_겹치면_한쪽만_실행되고_다른쪽은_409() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(inv -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return null;
        }).when(deletePostUseCase).deletePost(any(), any(), anyBoolean());

        CompletableFuture<ApiResponse<Void>> first =
                CompletableFuture.supplyAsync(() -> controller.deletePost(userId, null, postId));
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> controller.deletePost(userId, null, postId))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getReason()).isEqualTo("게시물을 삭제하는 중이에요.");
                });

        release.countDown();
        assertThat(first.get(5, TimeUnit.SECONDS).status()).isEqualTo(204);
        // 점수 차감이 들어 있는 deletePost가 한 번만 실행됐다
        verify(deletePostUseCase, times(1)).deletePost(any(), any(), anyBoolean());
    }

    @Test
    void 처리_중인_좋아요와_겹친_요청은_실행하지_않고_200() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(inv -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return null;
        }).when(likePostUseCase).like(postId, userId);

        CompletableFuture<ApiResponse<Void>> first =
                CompletableFuture.supplyAsync(() -> controller.like(userId, postId));
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        // 좋아요·취소는 같은 키를 공유한다
        assertThat(controller.like(userId, postId).status()).isEqualTo(200);
        assertThat(controller.unlike(userId, postId).status()).isEqualTo(200);

        release.countDown();
        assertThat(first.get(5, TimeUnit.SECONDS).status()).isEqualTo(200);
        verify(likePostUseCase, times(1)).like(postId, userId);
        verify(unlikePostUseCase, never()).unlike(any(), any());
    }
}
