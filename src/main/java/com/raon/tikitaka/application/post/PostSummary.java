package com.raon.tikitaka.application.post;

import com.raon.tikitaka.domain.post.Post;

/**
 * 게시물 목록 한 건. likeCount는 post_like 집계이며 Post 자체에는 없다.
 */
public record PostSummary(Post post, int likeCount) {
}
