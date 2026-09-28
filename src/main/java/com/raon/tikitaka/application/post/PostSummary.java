package com.raon.tikitaka.application.post;

import com.raon.tikitaka.domain.post.Post;

/**
 * 게시물 목록 한 건. likeCount/likedByMe는 post_like 집계이며 Post 자체에는 없다.
 * likedByMe는 비로그인 조회(userId == null)면 항상 false다.
 */
public record PostSummary(Post post, int likeCount, boolean likedByMe) {
}
