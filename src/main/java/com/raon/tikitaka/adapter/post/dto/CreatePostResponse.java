package com.raon.tikitaka.adapter.post.dto;

import com.raon.tikitaka.domain.post.PostStatus;

import java.util.UUID;

/**
 * 게시물 생성 응답. 생성된 post_id와 심사 상태를 담는다.
 * 클라이언트가 업로드 직후 상세로 이동할 때 목록을 다시 조회해
 * 최신 글을 방금 올린 글이라고 가정하던 우회를 없애기 위해 내려준다.
 * 동기 방식이면 DONE, 비동기 방식이면 PENDING이다.
 */
public record CreatePostResponse(UUID postId, String status) {

    public static CreatePostResponse of(UUID postId, PostStatus status) {
        return new CreatePostResponse(postId, status.name());
    }
}
