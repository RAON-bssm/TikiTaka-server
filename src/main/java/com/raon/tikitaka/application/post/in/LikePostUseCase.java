package com.raon.tikitaka.application.post.in;

import java.util.UUID;

public interface LikePostUseCase {

    /**
     * 게시물에 좋아요를 남긴다. 이미 눌렀으면 아무것도 하지 않는다(idempotent).
     */
    void like(UUID postId, UUID userId);
}
