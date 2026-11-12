package com.raon.tikitaka.adapter.post.dto;

import com.raon.tikitaka.adapter.equipment.dto.EquipmentResponse;
import com.raon.tikitaka.application.post.PostSummary;
import com.raon.tikitaka.domain.post.Post;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 게시판 글 목록 한 건. user_equipment는 GET /api/equipment/{userId}와 같은 모양의 작성자 착용 정보이고,
 * 착용한 것이 없으면 모든 필드가 빠진 빈 객체다.
 */
public record PostSummaryResponse(
        UUID postId,
        UUID userId,
        String userName,
        EquipmentResponse userEquipment,
        String postImage,
        String postImageUrl,
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
