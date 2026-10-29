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
import com.raon.tikitaka.application.review.in.ReviewUseCase;
import com.raon.tikitaka.application.storage.in.StorageUseCase;
import com.raon.tikitaka.global.config.PostReviewProperties;
import com.raon.tikitaka.global.guard.RequestGuard;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
 * 업로드 한도가 S3 업로드 전, 게시판 접근 검증 뒤에 걸리는지 본다. 동기·비동기 경로 모두 같다.
 */
class PostControllerUploadLimitTest {

    private final CreatePostUseCase createPostUseCase = mock(CreatePostUseCase.class);
    private final StorageUseCase storageUseCase = mock(StorageUseCase.class);
    private final ReviewUseCase reviewUseCase = mock(ReviewUseCase.class);
    private final GetBoardUseCase getBoardUseCase = mock(GetBoardUseCase.class);
    private final CheckPostUploadLimitUseCase uploadLimit = mock(CheckPostUploadLimitUseCase.class);

    private final UUID authorId = UUID.randomUUID();
    private final MockMultipartFile image = new MockMultipartFile("image", "photo.jpg", "image/jpeg", new byte[]{1, 2});

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 한도에_걸리면_S3_업로드와_AI_심사와_저장이_일어나지_않는다(boolean async) {
        doThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "10분에 2번까지 올릴 수 있어요. 7분 후에 다시 올려주세요."))
                .when(uploadLimit).checkUploadLimit(authorId);

        assertThatThrownBy(() -> controller(async).createPost(authorId, new CreatePostRequest(3L, "내용", image)))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(e.getReason()).isEqualTo("10분에 2번까지 올릴 수 있어요. 7분 후에 다시 올려주세요.");
                });
        verify(getBoardUseCase).getMission(3L, authorId);
        verify(storageUseCase, never()).uploadImage(any(), any(), any());
        verify(reviewUseCase, never()).evaluate(any(), any(), any(), any());
        verify(createPostUseCase, never()).createPost(any(), any(), any(), any(), anyInt(), any());
        verify(createPostUseCase, never()).createPending(any(), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 접근할_수_없는_게시판이면_한도보다_404가_먼저_나온다(boolean async) {
        when(getBoardUseCase.getMission(3L, authorId))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "종료되었거나 접근할 수 없는 게시판입니다."));

        assertThatThrownBy(() -> controller(async).createPost(authorId, new CreatePostRequest(3L, "내용", image)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(uploadLimit, never()).checkUploadLimit(any());
        verify(storageUseCase, never()).uploadImage(any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 이미지가_없으면_한도를_보기_전에_400(boolean async) {
        assertThatThrownBy(() -> controller(async).createPost(authorId, new CreatePostRequest(3L, "내용", null)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(uploadLimit, never()).checkUploadLimit(any());
    }

    private PostController controller(boolean async) {
        return new PostController(mock(GetPostListUseCase.class), mock(GetPostDetailUseCase.class), createPostUseCase,
                mock(UpdatePostUseCase.class), mock(DeletePostUseCase.class), mock(LikePostUseCase.class),
                mock(UnlikePostUseCase.class), storageUseCase, reviewUseCase, getBoardUseCase, new RequestGuard(),
                new PostReviewProperties(async, 1, 4, 5, Duration.ofMinutes(1), Duration.ofMinutes(5)), uploadLimit);
    }
}
