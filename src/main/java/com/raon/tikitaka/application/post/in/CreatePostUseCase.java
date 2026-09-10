package com.raon.tikitaka.application.post.in;

import java.util.UUID;

public interface CreatePostUseCase {

    void createPost(UUID authorId, Long boardId, String content, String postImage, Integer score, String aiReview);
}
