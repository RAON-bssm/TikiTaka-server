package com.raon.tikitaka.adapter.post.dto;

import com.raon.tikitaka.application.post.PostPage;
import com.raon.tikitaka.application.post.PostSummary;

import java.util.List;

/**
 * 글 목록 응답. next_cursor는 페이징 요청(size나 cursor를 보낸 경우)에서 다음 페이지가 있을 때만 들어가고,
 * 마지막 페이지이거나 파라미터 없이 전체를 받은 경우에는 null이라 응답에서 빠진다(non_null).
 */
public record PostListResponse(List<PostSummaryResponse> post, String nextCursor) {

    public static PostListResponse from(List<PostSummary> posts) {
        return new PostListResponse(posts.stream().map(PostSummaryResponse::from).toList(), null);
    }

    public static PostListResponse from(PostPage page) {
        return new PostListResponse(page.posts().stream().map(PostSummaryResponse::from).toList(), page.nextCursor());
    }
}
