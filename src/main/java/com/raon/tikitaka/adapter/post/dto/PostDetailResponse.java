package com.raon.tikitaka.adapter.post.dto;

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
        String location,
        Integer likeCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static PostDetailResponse from(Post post) {
        return new PostDetailResponse(
                post.getUserId().getUserId(),
                post.getUserId().getUserName(),
                post.getPostImage(),
                post.getContent(),
                post.getScore(),
                post.getAiReview(),
                post.getLocation(),
                0,
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
