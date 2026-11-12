package com.raon.tikitaka.adapter.post.dto;

import com.raon.tikitaka.adapter.equipment.dto.EquipmentResponse;
import com.raon.tikitaka.application.post.PostSummary;
import com.raon.tikitaka.domain.post.Post;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 게시판 글 목록 한 건. user_equipment는 GET /api/equipment/{userId}와 같은 모양의 작성자 착용 정보이고,
 * 착용한 것이 없으면 모든 필드가 빠진 빈 객체다.
 * post_thumbnail과 post_thumbnail_url은 목록 카드용 작은 이미지의 S3 key와 조회 URL이다. 썸네일이 없는 글은
 * 원본(post_image, post_image_url)과 같은 값이 들어가므로 앱은 카드에서 이 둘만 쓰면 된다.
 * 조회 URL은 요청마다 서명이 바뀌므로 앱의 이미지 캐시 키는 URL 대신 key(post_thumbnail)로 잡는다.
 */
public record PostSummaryResponse(
        UUID postId,
        UUID userId,
        String userName,
        EquipmentResponse userEquipment,
        String postImage,
        String postImageUrl,
        String postThumbnail,
        String postThumbnailUrl,
        Integer score,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String content,
        String cityName,
        String location,
        Integer likeCount,
        boolean likedByMe
) {

    public static PostSummaryResponse from(PostSummary summary) {
        Post post = summary.post();
        return new PostSummaryResponse(
                post.getPostId(),
                post.getUserId().getUserId(),
                post.getUserId().getUserName(),
                EquipmentResponse.from(summary.authorEquipment()),
                post.getPostImage(),
                summary.postImageUrl(),
                summary.postThumbnail(),
                summary.postThumbnailUrl(),
                post.getScore(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                post.getContent(),
                post.getTeamLocation().getCityName(),
                post.getLocation(),
                summary.likeCount(),
                summary.likedByMe()
        );
    }
}
