package com.raon.tikitaka.application.post;

import com.raon.tikitaka.domain.post.Post;

/**
 * 게시물 상세 조회 결과. likeCount/likedByMe는 post_like 집계이며 Post 자체에는 없다.
 * likedByMe는 비로그인 조회(userId == null)면 항상 false다.
 */
public record PostDetail(Post post, int likeCount, boolean likedByMe) {
}
