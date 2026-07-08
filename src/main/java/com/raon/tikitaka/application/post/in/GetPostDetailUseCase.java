package com.raon.tikitaka.application.post.in;

import com.raon.tikitaka.domain.post.Post;

import java.util.UUID;

public interface GetPostDetailUseCase {

    Post getPost(UUID postId);
}
