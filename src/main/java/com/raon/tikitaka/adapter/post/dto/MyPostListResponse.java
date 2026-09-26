package com.raon.tikitaka.adapter.post.dto;

import com.raon.tikitaka.domain.post.Post;

import java.util.List;

public record MyPostListResponse(List<MyPostSummaryResponse> post) {

    public static MyPostListResponse from(List<Post> posts) {
        return new MyPostListResponse(posts.stream().map(MyPostSummaryResponse::from).toList());
    }
}
