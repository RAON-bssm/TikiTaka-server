package com.raon.tikitaka.application.post;

import java.util.UUID;

/**
 * 게시물별 좋아요 수 한 건. 네이티브 쿼리의 별칭과 getter 이름을 맞춘 인터페이스 프로젝션이다.
 */
public interface PostLikeCountRow {

    UUID getPostId();

    Long getLikeCount();
}
