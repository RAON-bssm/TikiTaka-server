package com.raon.tikitaka.application.post.in;

import java.util.UUID;

public interface UnlikePostUseCase {

    /**
     * 게시물의 좋아요를 취소한다. 누른 적이 없으면 아무것도 하지 않는다(idempotent).
     */
    void unlike(UUID postId, UUID userId);
}
