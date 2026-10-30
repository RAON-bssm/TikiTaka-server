package com.raon.tikitaka.adapter.post;

import com.raon.tikitaka.adapter.post.dto.CreatePostRequest;
import com.raon.tikitaka.application.board.in.GetBoardUseCase;
import com.raon.tikitaka.application.post.in.CheckPostUploadLimitUseCase;
import com.raon.tikitaka.application.post.in.CreatePostUseCase;
import com.raon.tikitaka.application.post.in.DeletePostUseCase;
import com.raon.tikitaka.application.post.in.GetPostDetailUseCase;
import com.raon.tikitaka.application.post.in.GetPostListUseCase;
import com.raon.tikitaka.application.post.in.LikePostUseCase;
import com.raon.tikitaka.application.post.in.UnlikePostUseCase;
import com.raon.tikitaka.application.post.in.UpdatePostUseCase;
import com.raon.tikitaka.application.review.AiReviewInvalidResponseException;
import com.raon.tikitaka.application.review.AiReviewRejectedException;
import com.raon.tikitaka.application.review.AiReviewRetryableException;
import com.raon.tikitaka.application.review.in.ReviewUseCase;
import com.raon.tikitaka.application.storage.in.StorageUseCase;
import com.raon.tikitaka.global.config.PostReviewProperties;
import com.raon.tikitaka.global.guard.RequestGuard;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * post-review.async=false(동기 업로드)에서 AI 심사가 실패했을 때의 응답 코드. 어떤 경우든 게시물은 만들지 않는다.
 */
class PostControllerSyncReviewErrorTest {

    private final CreatePostUseCase createPostUseCase = mock(CreatePostUseCase.class);
    private final StorageUseCase storageUseCase = mock(StorageUseCase.class);
    private final ReviewUseCase reviewUseCase = mock(ReviewUseCase.class);
    private final PostController controller = new PostController(mock(GetPostListUseCase.class),
            mock(GetPostDetailUseCase.class), createPostUseCase, mock(UpdatePostUseCase.class),
            mock(DeletePostUseCase.class), mock(LikePostUseCase.class), mock(UnlikePostUseCase.class),
            storageUseCase, reviewUseCase, mock(GetBoardUseCase.class), new RequestGuard(),
            new PostReviewProperties(false, 1, 4, 5, Duration.ofMinutes(1), Duration.ofMinutes(5)),
            mock(CheckPostUploadLimitUseCase.class));

    private final UUID authorId = UUID.randomUUID();
    private final MockMultipartFile image = new MockMultipartFile("image", "photo.jpg", "image/jpeg", new byte[]{1, 2});

    @Test
    void 일시_오류는_503으로_끊고_게시물을_만들지_않는다() {
        when(reviewUseCase.evaluate(any(), any(), any(), any()))
                .thenThrow(new AiReviewRetryableException("Gemini 일시 오류 status=429", null, null));

        assertThat(statusOf()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        verify(createPostUseCase, never()).createPost(any(), any(), any(), any(), anyInt(), any());
    }

    @Test
    void 타임아웃도_일시_오류라_503() {
        when(reviewUseCase.evaluate(any(), any(), any(), any()))
                .thenThrow(new AiReviewRetryableException("Gemini 호출 대기 시간 초과", null, new IllegalStateException()));

        assertThat(statusOf()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void 응답_형식_오류는_일시_오류의_하위_타입이지만_502를_유지한다() {
        when(reviewUseCase.evaluate(any(), any(), any(), any()))
                .thenThrow(new AiReviewInvalidResponseException("score=null"));

        assertThat(statusOf()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void Gemini가_거절한_요청은_502() {
        when(reviewUseCase.evaluate(any(), any(), any(), any()))
                .thenThrow(new AiReviewRejectedException("Gemini 요청 거절 status=400", 400, null));

        assertThat(statusOf()).isEqualTo(HttpStatus.BAD_GATEWAY);
        verify(createPostUseCase, never()).createPost(any(), any(), any(), any(), anyInt(), any());
    }

    private HttpStatus statusOf() {
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> controller.createPost(authorId, new CreatePostRequest(3L, "내용", image)));
        assertThat(thrown).isInstanceOf(ResponseStatusException.class);
        return HttpStatus.valueOf(((ResponseStatusException) thrown).getStatusCode().value());
    }
}
