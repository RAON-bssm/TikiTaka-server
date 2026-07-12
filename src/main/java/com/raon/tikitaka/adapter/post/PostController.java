package com.raon.tikitaka.adapter.post;

import com.raon.tikitaka.adapter.post.dto.CreatePostRequest;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
    public ApiResponse<Void> createPost(
            @RequestHeader("Authorization") String authorization,
            @ModelAttribute CreatePostRequest request
    ) {
        UUID authorId = resolveUserId(authorization);
        MultipartFile image = validateImage(request.image());
        byte[] imageBytes = readImageBytes(image);

        String mission = getBoardUseCase.getMission(request.boardId());
        String key = storageUseCase.uploadImage(imageBytes, image.getOriginalFilename(), image.getContentType());
        AiReviewResult review = reviewUseCase.evaluate(mission, request.content(), imageBytes, image.getContentType());

        createPostUseCase.createPost(authorId, request.boardId(), request.content(), key, review.score(), review.review());
        return ApiResponse.of(201, "게시물 생성 성공", null);
    }

    @PatchMapping("/patch/{postId}")
    public ApiResponse<Void> updatePost(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "role", required = false) String role,
            @PathVariable UUID postId,
            @RequestBody UpdatePostRequest request
    ) {
        UUID requesterId = resolveUserId(authorization);
        updatePostUseCase.updatePost(postId, requesterId, isAdmin(role), request.content());
        return ApiResponse.of(204, "게시물 수정 성공", null);
    }

    @PatchMapping("/delete/{postId}")
    public ApiResponse<Void> deletePost(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "role", required = false) String role,
            @PathVariable UUID postId
    ) {
        UUID requesterId = resolveUserId(authorization);
        deletePostUseCase.deletePost(postId, requesterId, isAdmin(role));
        return ApiResponse.of(204, "게시물 삭제 성공", null);
    }

    private boolean isAdmin(String role) {
        return "admin".equalsIgnoreCase(role);
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

    private UUID resolveUserId(String authorization) {
        if (authorization == null || authorization.isBlank() || authorization.equalsIgnoreCase("null")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        String token = authorization.startsWith("Bearer ") ? authorization.substring(7) : authorization;
        try {
            return UUID.fromString(token);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다.");
        }
    }
}
