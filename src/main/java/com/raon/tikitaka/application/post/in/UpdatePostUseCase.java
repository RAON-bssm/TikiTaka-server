package com.raon.tikitaka.application.post.in;

import java.util.UUID;

public interface UpdatePostUseCase {

    void updatePost(UUID postId, UUID requesterId, boolean isAdmin, String content);
}
