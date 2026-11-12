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
import com.raon.tikitaka.application.review.AiReviewResult;
import com.raon.tikitaka.application.review.in.ReviewUseCase;
import com.raon.tikitaka.application.storage.UploadedImage;
import com.raon.tikitaka.application.storage.in.StorageUseCase;
import com.raon.tikitaka.global.config.PostReviewProperties;
import com.raon.tikitaka.global.guard.RequestGuard;
import com.raon.tikitaka.global.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 동기 업로드(post-review.async=false)에서 원본과 썸네일을 함께 올리고 두 key를 글 저장에 넘기는지 본다.
 * 비동기 업로드는 PostControllerAsyncTest가 본다.
 */
class PostControllerThumbnailUploadTest {

    private final CreatePostUseCase createPostUseCase = mock(CreatePostUseCase.class);
    private final StorageUseCase storageUseCase = mock(StorageUseCase.class);
    private final ReviewUseCase reviewUseCase = mock(ReviewUseCase.class);
    private final GetBoardUseCase getBoardUseCase = mock(GetBoardUseCase.class);
    private final PostController controller = new PostController(mock(GetPostListUseCase.class),
            mock(GetPostDetailUseCase.class), createPostUseCase, mock(UpdatePostUseCase.class),
            mock(DeletePostUseCase.class), mock(LikePostUseCase.class), mock(UnlikePostUseCase.class),
            storageUseCase, reviewUseCase, getBoardUseCase, new RequestGuard(),
            new PostReviewProperties(false, 1, 4, 5, Duration.ofMinutes(1), Duration.ofMinutes(5)),
            mock(CheckPostUploadLimitUseCase.class));

    private final UUID authorId = UUID.randomUUID();
    private final UUID postId = UUID.randomUUID();
    private final byte[] bytes = {1, 2};
    private final MockMultipartFile image = new MockMultipartFile("image", "photo.jpg", "image/jpeg", bytes);

    @Test
    void 원본과_썸네일_key를_함께_저장한다() {
        when(getBoardUseCase.getMission(3L, authorId)).thenReturn("귀여운 고양이");
        when(storageUseCase.uploadImageWithThumbnail(bytes, "photo.jpg", "image/jpeg"))
                .thenReturn(new UploadedImage("key-photo.jpg", "thumbnails/thumb.jpg"));
        when(reviewUseCase.evaluate(any(), any(), any(), any())).thenReturn(new AiReviewResult(80, "좋아요"));
        when(createPostUseCase.createPost(authorId, 3L, "내용", "key-photo.jpg", "thumbnails/thumb.jpg", 80, "좋아요"))
                .thenReturn(postId);

        ApiResponse<CreatePostResponse> response = controller.createPost(authorId,
                new CreatePostRequest(3L, "내용", image));

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.data().postId()).isEqualTo(postId);
        verify(createPostUseCase).createPost(authorId, 3L, "내용", "key-photo.jpg", "thumbnails/thumb.jpg", 80, "좋아요");
    }

    @Test
    void 썸네일이_없으면_null로_넘기고_업로드는_성공한다() {
        when(getBoardUseCase.getMission(3L, authorId)).thenReturn("귀여운 고양이");
        when(storageUseCase.uploadImageWithThumbnail(bytes, "photo.jpg", "image/jpeg"))
                .thenReturn(new UploadedImage("key-photo.jpg", null));
        when(reviewUseCase.evaluate(any(), any(), any(), any())).thenReturn(new AiReviewResult(80, "좋아요"));
        when(createPostUseCase.createPost(authorId, 3L, "내용", "key-photo.jpg", null, 80, "좋아요")).thenReturn(postId);

        ApiResponse<CreatePostResponse> response = controller.createPost(authorId,
                new CreatePostRequest(3L, "내용", image));

        assertThat(response.status()).isEqualTo(201);
        verify(createPostUseCase).createPost(authorId, 3L, "내용", "key-photo.jpg", null, 80, "좋아요");
    }
}
