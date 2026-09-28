package com.raon.tikitaka.application.post;

import com.raon.tikitaka.domain.post.Post;

/**
 * 마이페이지 "게시물 보관함" 목록 한 건. likeCount/likedByMe는 post_like 집계이며
 * Post 자체에는 없다. 항상 호출자 본인의 글만 담기므로 likedByMe는 "내가 내 글에
 * 좋아요를 눌렀는지"를 뜻한다.
 */
public record MyPostSummary(Post post, int likeCount, boolean likedByMe) {
}
