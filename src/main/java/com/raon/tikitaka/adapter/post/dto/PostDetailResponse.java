package com.raon.tikitaka.adapter.post.dto;

import com.raon.tikitaka.application.post.PostDetail;
import com.raon.tikitaka.domain.post.Post;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 게시물 상세 응답. status는 PostStatus 이름이다. 심사가 끝나지 않은 글은 score와 ai_review가 null이라
 * 응답에서 필드 자체가 빠진다(jackson default-property-inclusion: non_null).
 */
public record PostDetailResponse(
        UUID userId,
        String userName,
        String postImage,
        String content,
        Integer score,
        String aiReview,
        String status,
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
                post.getStatus().name(),
                post.getTeamLocation().getCityName(),
                post.getLocation(),
                detail.likeCount(),
                detail.likedByMe(),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
