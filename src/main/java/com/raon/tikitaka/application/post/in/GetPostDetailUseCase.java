package com.raon.tikitaka.application.post.in;

import com.raon.tikitaka.application.post.PostDetail;

import java.util.UUID;

public interface GetPostDetailUseCase {

    /**
     * userId는 비로그인 조회면 null이다. 이 경우 PostDetail.likedByMe는 항상 false다.
     */
    PostDetail getPost(UUID postId, UUID userId);
}
