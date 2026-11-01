package com.raon.tikitaka.adapter.post;

import com.raon.tikitaka.adapter.post.dto.CreatePostRequest;
import com.raon.tikitaka.adapter.post.dto.CreatePostResponse;
import com.raon.tikitaka.application.board.in.GetBoardUseCase;
import com.raon.tikitaka.application.post.in.CheckPostUploadLimitUseCase;
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
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * post-review.async=true일 때의 업로드. AI를 부르지 않고 PENDING으로 저장한 뒤 바로 응답하는지 본다.
 */
class PostControllerAsyncTest {

    private final CreatePostUseCase createPostUseCase = mock(CreatePostUseCase.class);
    private final StorageUseCase storageUseCase = mock(StorageUseCase.class);
    private final ReviewUseCase reviewUseCase = mock(ReviewUseCase.class);
    private final GetBoardUseCase getBoardUseCase = mock(GetBoardUseCase.class);
    private final CheckPostUploadLimitUseCase uploadLimit = mock(CheckPostUploadLimitUseCase.class);
    private final PostController controller = new PostController(mock(GetPostListUseCase.class),
            mock(GetPostDetailUseCase.class), createPostUseCase, mock(UpdatePostUseCase.class),
            mock(DeletePostUseCase.class), mock(LikePostUseCase.class), mock(UnlikePostUseCase.class),
            storageUseCase, reviewUseCase, getBoardUseCase, new RequestGuard(),
            new PostReviewProperties(true, 1, 4, 5, Duration.ofMinutes(1), Duration.ofMinutes(5)),
            uploadLimit);

    private final UUID authorId = UUID.randomUUID();
    private final UUID postId = UUID.randomUUID();
    private final MockMultipartFile image = new MockMultipartFile("image", "photo.jpg", "image/jpeg", new byte[]{1, 2});

    @Test
    void 접근을_검증하고_S3_업로드_후_PENDING으로_저장해_바로_응답한다() {
        when(storageUseCase.uploadImage(any(), any(), any())).thenReturn("key-photo.jpg");
        when(createPostUseCase.createPending(authorId, 3L, "내용", "key-photo.jpg")).thenReturn(postId);

        ApiResponse<CreatePostResponse> response = controller.createPost(authorId,
                new CreatePostRequest(3L, "내용", image));

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.data()).isEqualTo(new CreatePostResponse(postId, "PENDING"));
        verify(getBoardUseCase).getMission(3L, authorId);
        verify(reviewUseCase, never()).evaluate(any(), any(), any(), any());
        verify(createPostUseCase, never()).createPost(any(), any(), any(), any(), anyInt(), any());
    }

    @Test
    void 대기열이_가득_차면_S3에_올리지_않는다() {
        doThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "가득 참"))
                .when(uploadLimit).checkQueueCapacity();

        assertThatThrownBy(() -> controller.createPost(authorId, new CreatePostRequest(3L, "내용", image)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        verify(storageUseCase, never()).uploadImage(any(), any(), any());
        verify(createPostUseCase, never()).createPending(any(), any(), any(), any());
    }

    @Test
    void 게시판에_접근할_수_없으면_S3에_올리지_않는다() {
        when(getBoardUseCase.getMission(3L, authorId))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "종료되었거나 접근할 수 없는 게시판입니다."));

        assertThatThrownBy(() -> controller.createPost(authorId, new CreatePostRequest(3L, "내용", image)))
                .isInstanceOf(ResponseStatusException.class);
        verify(storageUseCase, never()).uploadImage(any(), any(), any());
        verify(createPostUseCase, never()).createPending(any(), any(), any(), any());
    }
}
