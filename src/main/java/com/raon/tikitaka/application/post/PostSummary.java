package com.raon.tikitaka.application.post;

import com.raon.tikitaka.application.equipment.EquippedItems;
import com.raon.tikitaka.domain.post.Post;

/**
 * 게시물 목록 한 건. likeCount/likedByMe는 post_like 집계이며 Post 자체에는 없다.
 * likedByMe는 비로그인 조회(userId == null)면 항상 false다.
 * postImageUrl은 post_image로 발급한 조회용 presigned URL이고, 이미지가 없거나 발급에 실패하면 null이다.
 * authorEquipment는 작성자의 착용 정보이고 null이 아니다. 착용한 것이 없으면 빈 EquippedItems다.
 */
public record PostSummary(Post post, int likeCount, boolean likedByMe, String postImageUrl,
                          EquippedItems authorEquipment) {
}
