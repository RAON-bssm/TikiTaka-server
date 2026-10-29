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
import com.raon.tikitaka.global.config.PostReviewProperties;
import com.raon.tikitaka.global.guard.RequestGuard;
import com.raon.tikitaka.global.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
                mock(GetBoardUseCase.class), new RequestGuard(), syncProperties());
    }

    private static PostReviewProperties syncProperties() {
        return new PostReviewProperties(false, 1, 4, 5, Duration.ofMinutes(1), Duration.ofMinutes(5));
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
    void 좋아요_처리_중에_취소를_누르면_버려지지_않고_좋아요_뒤에_실행된다() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        doAnswer(inv -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            order.add("like");
            return null;
        }).when(likePostUseCase).like(postId, userId);
        doAnswer(inv -> {
            order.add("unlike");
            return null;
        }).when(unlikePostUseCase).unlike(postId, userId);

        CompletableFuture<ApiResponse<Void>> like =
                CompletableFuture.supplyAsync(() -> controller.like(userId, postId));
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<ApiResponse<Void>> unlike =
                CompletableFuture.supplyAsync(() -> controller.unlike(userId, postId));
        Thread.sleep(100);
        // 좋아요가 끝나기 전에는 취소가 실행되지 않고 기다린다
        assertThat(order).isEmpty();

        release.countDown();
        assertThat(like.get(5, TimeUnit.SECONDS).status()).isEqualTo(200);
        assertThat(unlike.get(5, TimeUnit.SECONDS).status()).isEqualTo(200);
        assertThat(order).containsExactly("like", "unlike");
    }
}
