package com.raon.tikitaka.adapter.post;

import com.raon.tikitaka.application.post.in.RetryPostReviewUseCase;
import com.raon.tikitaka.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 게시물 관리자 API. /api/admin/**는 SecurityConfig에서 ADMIN 권한만 통과한다.
 */
@RestController
@RequestMapping("/api/admin/post")
@RequiredArgsConstructor
public class PostAdminController {

    private final RetryPostReviewUseCase retryPostReviewUseCase;

    /**
     * AI 일시 오류가 재시도 한도를 넘겨 FAILED가 된 글을 다시 심사 대기열에 넣는다.
     * 심사는 워커가 다음 틱부터 다시 하고, 통과하면 그때 점수와 포인트가 반영된다.
     */
    @PostMapping("/{postId}/retry-review")
    public ApiResponse<Void> retryReview(@PathVariable UUID postId) {
        retryPostReviewUseCase.retryReview(postId);
        return ApiResponse.of(200, "재심사 요청 완료", null);
    }
}
