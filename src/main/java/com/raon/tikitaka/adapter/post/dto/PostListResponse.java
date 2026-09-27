package com.raon.tikitaka.adapter.post.dto;

import com.raon.tikitaka.application.post.PostSummary;

import java.util.List;

public record PostListResponse(List<PostSummaryResponse> post) {

    public static PostListResponse from(List<PostSummary> posts) {
        return new PostListResponse(posts.stream().map(PostSummaryResponse::from).toList());
    }
}
