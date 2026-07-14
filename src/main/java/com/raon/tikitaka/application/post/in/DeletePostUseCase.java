package com.raon.tikitaka.application.post.in;

import java.util.UUID;

public interface DeletePostUseCase {

    void deletePost(UUID postId, UUID requesterId, boolean isAdmin);
}
