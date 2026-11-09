package com.raon.tikitaka.adapter.post.dto;

import com.raon.tikitaka.application.post.PostDetail;
import com.raon.tikitaka.domain.post.Post;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 게시물 상세 응답. status는 PostStatus 이름이다. 심사가 끝나지 않은 글은 score와 ai_review가 null이라
 * 응답에서 필드 자체가 빠진다(jackson default-property-inclusion: non_null).
 * post_image는 S3 key 그대로(구버전 앱이 이 값으로 view-url을 부른다)이고, post_image_url은 바로 쓸 수 있는
 * 조회 URL이다. 이미지가 없거나 URL 발급에 실패하면 post_image_url이 빠진다.
 */
public record PostDetailResponse(
        UUID userId,
        String userName,
        String postImage,
        String postImageUrl,
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
                detail.postImageUrl(),
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
