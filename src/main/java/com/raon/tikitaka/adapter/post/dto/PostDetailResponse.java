package com.raon.tikitaka.adapter.post.dto;

import com.raon.tikitaka.application.post.PostDetail;
import com.raon.tikitaka.domain.post.Post;

import java.time.LocalDateTime;
import java.util.UUID;

public record PostDetailResponse(
        UUID userId,
        String userName,
        String postImage,
        String content,
        Integer score,
        String aiReview,
        String cityName,
        String location,
        Integer likeCount,
        boolean likedByMe,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static PostDetailResponse from(PostDetail detail) {
        Post post = detail.post();
        return new PostDetailResponse(
                post.getUserId().getUserId(),
                post.getUserId().getUserName(),
                post.getPostImage(),
                post.getContent(),
                post.getScore(),
                post.getAiReview(),
                post.getTeamLocation().getCityName(),
                post.getLocation(),
                detail.likeCount(),
                detail.likedByMe(),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
