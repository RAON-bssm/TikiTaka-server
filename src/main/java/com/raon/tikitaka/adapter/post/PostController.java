package com.raon.tikitaka.adapter.post;

import com.raon.tikitaka.adapter.post.dto.CreatePostRequest;
import com.raon.tikitaka.adapter.post.dto.CreatePostResponse;
import com.raon.tikitaka.adapter.post.dto.PostDetailResponse;
import com.raon.tikitaka.adapter.post.dto.PostListResponse;
import com.raon.tikitaka.adapter.post.dto.UpdatePostRequest;
import com.raon.tikitaka.application.post.in.CheckPostUploadLimitUseCase;
import com.raon.tikitaka.application.post.in.CreatePostUseCase;
import com.raon.tikitaka.application.post.in.DeletePostUseCase;
import com.raon.tikitaka.application.post.in.GetPostDetailUseCase;
import com.raon.tikitaka.application.post.in.GetPostListUseCase;
import com.raon.tikitaka.application.post.in.LikePostUseCase;
import com.raon.tikitaka.application.post.in.UnlikePostUseCase;
import com.raon.tikitaka.application.post.in.UpdatePostUseCase;
import com.raon.tikitaka.application.board.in.GetBoardUseCase;
import com.raon.tikitaka.application.review.AiReviewConfigException;
import com.raon.tikitaka.application.review.AiReviewInvalidResponseException;
import com.raon.tikitaka.application.review.AiReviewRejectedException;
import com.raon.tikitaka.application.review.AiReviewResult;
import com.raon.tikitaka.application.review.AiReviewRetryableException;
import com.raon.tikitaka.application.review.in.ReviewUseCase;
import com.raon.tikitaka.application.storage.in.StorageUseCase;
import com.raon.tikitaka.domain.post.PostStatus;
import com.raon.tikitaka.global.config.PostReviewProperties;
import com.raon.tikitaka.global.guard.RequestGuard;
import com.raon.tikitaka.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final LikePostUseCase likePostUseCase;
    private final UnlikePostUseCase unlikePostUseCase;
    private final StorageUseCase storageUseCase;
    private final ReviewUseCase reviewUseCase;
    private final GetBoardUseCase getBoardUseCase;
    private final RequestGuard requestGuard;
    private final PostReviewProperties postReviewProperties;
    private final CheckPostUploadLimitUseCase checkPostUploadLimitUseCase;

    /**
     * GET /api/post/**는 SecurityConfig에서 permitAll이라 비로그인 조회 시
     * userId는 null로 들어오고, 이 경우 각 게시물의 좋아요 여부는 항상 false다.
     */
    @GetMapping("/{boardId:\\d+}")
    public ApiResponse<PostListResponse> getPosts(@AuthenticationPrincipal UUID userId, @PathVariable Long boardId) {
        PostListResponse response = PostListResponse.from(getPostListUseCase.getPosts(boardId, userId));
        return ApiResponse.of(200, "게시물 목록 조회 성공", response);
    }

    /**
     * GET /api/post/**는 SecurityConfig에서 permitAll이라 비로그인 조회 시
     * userId는 null로 들어오고, 이 경우 좋아요 여부는 항상 false다.
     * 심사가 끝나지 않은 글은 작성자 본인과 관리자만 볼 수 있어 관리자 여부를 같이 넘긴다.
     */
    @GetMapping("/{postId:" + UUID_PATTERN + "}")
    public ApiResponse<PostDetailResponse> getPost(@AuthenticationPrincipal UUID userId, Authentication authentication,
                                                   @PathVariable UUID postId) {
        PostDetailResponse response = PostDetailResponse.from(
                getPostDetailUseCase.getPost(postId, userId, isAdmin(authentication)));
        return ApiResponse.of(200, "게시물 조회 성공", response);
    }

    /**
     * 같은 게시물에 대한 좋아요·취소는 같은 키를 공유하고, 겹치면 앞선 요청이 끝난 뒤 순서대로 실행한다.
     * 좋아요를 누르자마자 취소를 눌러도 취소 요청이 버려지지 않고, 마지막에 누른 쪽이 최종 상태가 된다.
     */
    @PostMapping("/{postId:" + UUID_PATTERN + "}/like")
    public ApiResponse<Void> like(@AuthenticationPrincipal UUID userId, @PathVariable UUID postId) {
        requestGuard.executeWaiting(likeKey(userId, postId), LIKE_BUSY_MESSAGE, () -> likePostUseCase.like(postId, userId));
        return ApiResponse.of(200, "좋아요 성공", null);
    }

    @DeleteMapping("/{postId:" + UUID_PATTERN + "}/like")
    public ApiResponse<Void> unlike(@AuthenticationPrincipal UUID userId, @PathVariable UUID postId) {
        requestGuard.executeWaiting(likeKey(userId, postId), LIKE_BUSY_MESSAGE, () -> unlikePostUseCase.unlike(postId, userId));
        return ApiResponse.of(200, "좋아요 취소 성공", null);
    }

    /**
     * post-review.async가 true면 S3 업로드 후 PENDING으로 저장하고 바로 응답한다. AI 심사와 포인트·점수 반영은
     * 심사 워커(PostReviewWorker)가 뒤에서 한다. false면 기존처럼 이 요청 안에서 심사까지 끝낸다.
     */
    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreatePostResponse> createPost(
            @AuthenticationPrincipal UUID authorId,
            @ModelAttribute CreatePostRequest request
    ) {
        if (postReviewProperties.async()) {
            return createPending(authorId, request);
        }
        // 업로드 버튼 더블탭으로 S3 업로드·AI 심사·포인트 적립·점수 가산이 두 번 일어나지 않도록 본문 전체를 감싼다
        UUID postId = requestGuard.execute("post-create:" + authorId, "게시물을 등록하는 중이에요.", () -> {
            MultipartFile image = validateImage(request.image());
            byte[] imageBytes = readImageBytes(image);

            // 작성자가 지금 있는 동네 기준으로 게시판 접근을 검증한다. 종료된 라운드나 다른 동네 게시판이면 404
            String mission = getBoardUseCase.getMission(request.boardId(), authorId);
            // 업로드 한도는 S3 업로드 전에 본다. 거절할 요청에 업로드 비용을 쓰지 않고,
            // 작성자 키 안이라 같은 사용자의 동시 요청이 함께 통과하지 않는다
            checkPostUploadLimitUseCase.checkUploadLimit(authorId);
            String key = storageUseCase.uploadImage(imageBytes, image.getOriginalFilename(), image.getContentType());
            AiReviewResult review = evaluate(mission, request.content(), imageBytes, image.getContentType());
            int score = review.score();

            // 포인트 적립이 일어나는 구간만 포인트 키로 감싼다. 처리 시간이 긴 업로드·AI 호출 동안
            // 상점 구매나 뽑기까지 막지 않기 위해서다. 이 시점엔 S3 업로드와 AI 호출 비용을 이미 썼으므로
            // 겹친 구매·뽑기(수십 ms)가 끝나길 기다렸다가 등록하고, 바로 거절하지 않는다
            return requestGuard.executeWaiting(RequestGuard.pointsKey(authorId), RequestGuard.POINTS_BUSY_MESSAGE,
                    () -> createPostUseCase.createPost(authorId, request.boardId(), request.content(), key, score,
                            review.review()));
        });
        return ApiResponse.of(201, "게시물 생성 성공", CreatePostResponse.of(postId, PostStatus.DONE));
    }

    /**
     * 비동기 업로드. 포인트 적립이 이 요청에서 일어나지 않으므로 포인트 키로 감싸지 않는다.
     * 더블탭으로 S3 업로드와 글 저장이 두 번 일어나지 않도록 작성자 키는 그대로 건다.
     */
    private ApiResponse<CreatePostResponse> createPending(UUID authorId, CreatePostRequest request) {
        UUID postId = requestGuard.execute("post-create:" + authorId, "게시물을 등록하는 중이에요.", () -> {
            MultipartFile image = validateImage(request.image());
            byte[] imageBytes = readImageBytes(image);

            // 미션 값은 쓰지 않고 접근 검증만 한다. 종료된 라운드나 다른 동네 게시판이면 S3 업로드 전에 404
            getBoardUseCase.getMission(request.boardId(), authorId);
            checkPostUploadLimitUseCase.checkUploadLimit(authorId);
            // 대기열이 가득 차면 업로드 전에 거절한다. 심사가 밀린 채로 계속 쌓이지 않게 한다
            checkPostUploadLimitUseCase.checkQueueCapacity();
            String key = storageUseCase.uploadImage(imageBytes, image.getOriginalFilename(), image.getContentType());
            return createPostUseCase.createPending(authorId, request.boardId(), request.content(), key);
        });
        return ApiResponse.of(201, "게시물 생성 성공", CreatePostResponse.of(postId, PostStatus.PENDING));
    }

    @PatchMapping("/patch/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
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
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> deletePost(
            @AuthenticationPrincipal UUID requesterId,
            Authentication authentication,
            @PathVariable UUID postId
    ) {
        // 동시에 두 번 들어오면 둘 다 활성으로 읽고 점수를 두 번 차감한다. 작성자와 관리자가 겹칠 수도 있어 게시물 기준 키다
        requestGuard.execute("post-delete:" + postId, "게시물을 삭제하는 중이에요.",
                () -> deletePostUseCase.deletePost(postId, requesterId, isAdmin(authentication)));
        return ApiResponse.of(204, "게시물 삭제 성공", null);
    }

    private static final String LIKE_BUSY_MESSAGE = "이전 좋아요 요청을 처리하는 중이에요.";

    private static String likeKey(UUID userId, UUID postId) {
        return "like:" + userId + ":" + postId;
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
     * 동기 업로드 경로의 AI 심사. 점수 누락·범위 검증은 ReviewService로 옮겼고, 어떤 실패든 게시물을 만들지 않고 끊는다.
     * 응답 형식 오류는 502, 429·5xx·타임아웃·네트워크 오류 같은 일시 오류와 404(모델 설정 오류)는 503,
     * Gemini가 거절한 요청은 502다.
     * 형식 오류가 일시 오류의 하위 타입이라 먼저 잡는다.
     */
    private AiReviewResult evaluate(String mission, String content, byte[] imageBytes, String contentType) {
        try {
            return reviewUseCase.evaluate(mission, content, imageBytes, contentType);
        } catch (AiReviewInvalidResponseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "AI 심사 점수가 올바르지 않습니다. 잠시 후 다시 시도해주세요.");
        } catch (AiReviewRetryableException | AiReviewConfigException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI 심사가 지연되고 있어요. 잠시 후 다시 시도해주세요.");
        } catch (AiReviewRejectedException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "AI가 심사할 수 없는 게시물이에요. 이미지와 내용을 확인해주세요.");
        }
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
