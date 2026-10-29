package com.raon.tikitaka.application.post.review;

import com.raon.tikitaka.application.post.out.PostRepositoryPort;
import com.raon.tikitaka.application.post.out.PostReviewQueuePort;
import com.raon.tikitaka.domain.post.Post;
import com.raon.tikitaka.domain.post.PostStatus;
import com.raon.tikitaka.global.config.PostReviewProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 실패 횟수, 백오프, FAILED 전환. 상태를 바꾸기 전에 잠근 행이 PENDING인지 다시 보는지도 확인한다.
 */
class PostReviewQueueServiceTest {

    private final PostRepositoryPort postRepositoryPort = mock(PostRepositoryPort.class);
    private final PostReviewQueuePort queuePort = mock(PostReviewQueuePort.class);
    private final PostReviewQueueService service = new PostReviewQueueService(postRepositoryPort, queuePort,
            new PostReviewProperties(true, 1, 4, 5, Duration.ofMinutes(1), Duration.ofMinutes(5)));

    private final UUID postId = UUID.randomUUID();

    @ParameterizedTest
    @CsvSource({"0, 1, 1", "1, 2, 2", "2, 3, 4", "3, 4, 8"})
    void 실패하면_시도_횟수를_올리고_1_2_4_8분_뒤로_미룬다(int before, int after, long waitMinutes) {
        lockedPost(PostStatus.PENDING, before);

        PostStatus status = service.recordFailure(postId, null);

        assertThat(status).isEqualTo(PostStatus.PENDING);
        ArgumentCaptor<LocalDateTime> nextAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(queuePort).scheduleRetry(eq(postId), eq(after), nextAt.capture());
        assertThat(nextAt.getValue()).isCloseTo(LocalDateTime.now().plusMinutes(waitMinutes),
                within(5, java.time.temporal.ChronoUnit.SECONDS));
    }

    @Test
    void 다섯_번째_실패면_FAILED() {
        lockedPost(PostStatus.PENDING, 4);

        PostStatus status = service.recordFailure(postId, null);

        assertThat(status).isEqualTo(PostStatus.FAILED);
        verify(queuePort).finish(postId, PostStatus.FAILED, 5);
        verify(queuePort, never()).scheduleRetry(any(), anyInt(), any());
    }

    @Test
    void Gemini가_대기_시간을_주면_기본_백오프_대신_쓴다() {
        lockedPost(PostStatus.PENDING, 0);

        service.recordFailure(postId, Duration.ofSeconds(17));

        ArgumentCaptor<LocalDateTime> nextAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(queuePort).scheduleRetry(eq(postId), eq(1), nextAt.capture());
        assertThat(nextAt.getValue()).isCloseTo(LocalDateTime.now().plusSeconds(17),
                within(5, java.time.temporal.ChronoUnit.SECONDS));
    }

    @Test
    void 이미_PENDING이_아니면_실패를_기록하지_않는다() {
        lockedPost(PostStatus.EXPIRED, 0);

        assertThat(service.recordFailure(postId, null)).isEqualTo(PostStatus.EXPIRED);
        verify(queuePort, never()).scheduleRetry(any(), anyInt(), any());
        verify(queuePort, never()).finish(any(), any(), anyInt());
    }

    @Test
    void 종료_상태로_바꾸기는_PENDING일_때만() {
        lockedPost(PostStatus.DONE, 0);
        assertThat(service.finish(postId, PostStatus.REJECTED)).isFalse();
        verify(queuePort, never()).finish(any(), any(), anyInt());

        lockedPost(PostStatus.PENDING, 2);
        assertThat(service.finish(postId, PostStatus.REJECTED)).isTrue();
        verify(queuePort).finish(postId, PostStatus.REJECTED, 2);
    }

    @Test
    void 선점_해제는_시도_횟수를_건드리지_않는다() {
        lockedPost(PostStatus.PENDING, 3);

        service.release(postId);

        verify(queuePort).release(postId);
        verify(queuePort, never()).scheduleRetry(any(), anyInt(), any());
    }

    // ===== 관리자 재심사 =====

    @Test
    void FAILED_글은_재심사_대기열로_되돌린다() {
        lockedPost(PostStatus.FAILED, 5);

        service.retryReview(postId);

        verify(queuePort).reset(postId);
    }

    @ParameterizedTest
    @EnumSource(value = PostStatus.class, names = "FAILED", mode = EnumSource.Mode.EXCLUDE)
    void FAILED가_아니면_재심사는_409(PostStatus status) {
        lockedPost(status, 0);

        assertThatThrownBy(() -> service.retryReview(postId))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(queuePort, never()).reset(any());
    }

    @Test
    void 없거나_삭제된_글의_재심사는_404() {
        when(postRepositoryPort.findByIdForUpdate(postId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.retryReview(postId))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        Post deleted = mock(Post.class);
        when(deleted.isActive()).thenReturn(false);
        when(postRepositoryPort.findByIdForUpdate(postId)).thenReturn(Optional.of(deleted));
        assertThatThrownBy(() -> service.retryReview(postId))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(queuePort, never()).reset(any());
    }

    private void lockedPost(PostStatus status, int attempts) {
        Post post = mock(Post.class);
        when(post.isActive()).thenReturn(true);
        when(post.getStatus()).thenReturn(status);
        when(post.isPending()).thenReturn(status == PostStatus.PENDING);
        when(post.getReviewAttempts()).thenReturn(attempts);
        when(postRepositoryPort.findByIdForUpdate(postId)).thenReturn(Optional.of(post));
    }
}
