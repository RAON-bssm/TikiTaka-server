package com.raon.tikitaka.adapter.post.dto;

import com.raon.tikitaka.application.post.MyPostSummary;

import java.util.List;

public record MyPostListResponse(List<MyPostSummaryResponse> post) {

    public static MyPostListResponse from(List<MyPostSummary> posts) {
        return new MyPostListResponse(posts.stream().map(MyPostSummaryResponse::from).toList());
    }
}
