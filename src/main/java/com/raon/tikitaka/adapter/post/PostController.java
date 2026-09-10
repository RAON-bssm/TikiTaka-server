package com.raon.tikitaka.adapter.post;

import com.raon.tikitaka.adapter.post.dto.CreatePostRequest;
import com.raon.tikitaka.adapter.post.dto.CreatePostResponse;
import com.raon.tikitaka.adapter.post.dto.PostDetailResponse;
import com.raon.tikitaka.adapter.post.dto.PostListResponse;
import com.raon.tikitaka.adapter.post.dto.UpdatePostRequest;
import com.raon.tikitaka.application.post.in.CreatePostUseCase;
import com.raon.tikitaka.application.post.in.DeletePostUseCase;
import com.raon.tikitaka.application.post.in.GetPostDetailUseCase;
import com.raon.tikitaka.application.post.in.GetPostListUseCase;
import com.raon.tikitaka.application.post.in.UpdatePostUseCase;
import com.raon.tikitaka.application.board.in.GetBoardUseCase;
import com.raon.tikitaka.application.review.AiReviewResult;
import com.raon.tikitaka.application.review.in.ReviewUseCase;
import com.raon.tikitaka.application.storage.in.StorageUseCase;
import com.raon.tikitaka.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/post")
@RequiredArgsConstructor
public class PostController {

    private static final String UUID_PATTERN = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    private final GetPostListUseCase getPostListUseCase;
    private final GetPostDetailUseCase getPostDetailUseCase;
    private final CreatePostUseCase createPostUseCase;
    private final UpdatePostUseCase updatePostUseCase;
    private final DeletePostUseCase deletePostUseCase;
    private final StorageUseCase storageUseCase;
    private final ReviewUseCase reviewUseCase;
    private final GetBoardUseCase getBoardUseCase;

    @GetMapping("/{boardId:\\d+}")
    public ApiResponse<PostListResponse> getPosts(@PathVariable Long boardId) {
        PostListResponse response = PostListResponse.from(getPostListUseCase.getPosts(boardId));
        return ApiResponse.of(200, "게시물 목록 조회 성공", response);
    }

    @GetMapping("/{postId:" + UUID_PATTERN + "}")
    public ApiResponse<PostDetailResponse> getPost(@PathVariable UUID postId) {
        PostDetailResponse response = PostDetailResponse.from(getPostDetailUseCase.getPost(postId));
        return ApiResponse.of(200, "게시물 조회 성공", response);
    }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreatePostResponse> createPost(
            @AuthenticationPrincipal UUID authorId,
            @ModelAttribute CreatePostRequest request
    ) {
        MultipartFile image = validateImage(request.image());
        byte[] imageBytes = readImageBytes(image);

        // 작성자 기준으로 게시판 접근을 검증한다. 종료된 라운드나 남의 동네 게시판이면 404
        String mission = getBoardUseCase.getMission(request.boardId(), authorId);
        String key = storageUseCase.uploadImage(imageBytes, image.getOriginalFilename(), image.getContentType());
        AiReviewResult review = reviewUseCase.evaluate(mission, request.content(), imageBytes, image.getContentType());
        int score = validateScore(review.score());

        UUID postId = createPostUseCase.createPost(authorId, request.boardId(), request.content(), key, score, review.review());
        return ApiResponse.of(201, "게시물 생성 성공", CreatePostResponse.of(postId));
    }

    @PatchMapping("/patch/{postId}")
    public ApiResponse<Void> updatePost(
            @AuthenticationPrincipal UUID requesterId,
            Authentication authentication,
            @PathVariable UUID postId,
            @RequestBody UpdatePostRequest request
    ) {
        updatePostUseCase.updatePost(postId, requesterId, isAdmin(authentication), request.content());
        return ApiResponse.of(204, "게시물 수정 성공", null);
    }

    @PatchMapping("/delete/{postId}")
    public ApiResponse<Void> deletePost(
            @AuthenticationPrincipal UUID requesterId,
            Authentication authentication,
            @PathVariable UUID postId
    ) {
        deletePostUseCase.deletePost(postId, requesterId, isAdmin(authentication));
        return ApiResponse.of(204, "게시물 삭제 성공", null);
    }

    /**
     * JWT의 role로 관리자를 판별한다. 필터가 심어준 ROLE_ 권한을 검사한다.
     * 예전의 role 헤더 방식은 누구나 위조할 수 있어 폐기했다.
     */
    private boolean isAdmin(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if ("ROLE_ADMIN".equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gemini 응답에서 score가 누락되거나 범위를 벗어난 경우를 걸러낸다.
     * 누락 시 asInt()가 0을 돌려주는 함정 때문에 GeminiAdapter가 null을 반환하게 했고
     * 여기서 null이거나 범위 밖이면 게시물을 만들지 않고 502로 끊는다.
     */
    private int validateScore(Integer score) {
        if (score == null || score < 0 || score > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "AI 심사 점수가 올바르지 않습니다. 잠시 후 다시 시도해주세요.");
        }
        return score;
    }

    private MultipartFile validateImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미지는 필수입니다.");
        }
        return image;
    }

    private byte[] readImageBytes(MultipartFile image) {
        try {
            return image.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미지를 읽을 수 없습니다.");
        }
    }
}
