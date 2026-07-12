package com.raon.tikitaka.adapter.post.dto;

import com.raon.tikitaka.domain.post.Post;

import java.time.LocalDateTime;
import java.util.UUID;

public record PostSummaryResponse(
        UUID postId,
        UUID userId,
        String userName,
        String postImage,
        Integer score,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static PostSummaryResponse from(Post post) {
        return new PostSummaryResponse(
                post.getPostId(),
                post.getUserId().getUserId(),
                post.getUserId().getUserName(),
                post.getPostImage(),
                post.getScore(),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
