package com.raon.tikitaka.adapter.post.dto;

import com.raon.tikitaka.domain.post.Post;

import java.util.List;

public record PostListResponse(List<PostSummaryResponse> post) {

    public static PostListResponse from(List<Post> posts) {
        return new PostListResponse(posts.stream().map(PostSummaryResponse::from).toList());
    }
}
