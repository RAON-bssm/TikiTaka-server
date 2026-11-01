package com.raon.tikitaka.application.post.in;

import com.raon.tikitaka.application.post.PostDetail;

import java.util.UUID;

public interface GetPostDetailUseCase {

    /**
     * userId는 비로그인 조회면 null이다. 이 경우 PostDetail.likedByMe는 항상 false다.
     * 심사가 끝나지 않은 글(DONE이 아닌 글)은 작성자 본인과 관리자(isAdmin)만 볼 수 있고 그 외에는 404다.
     */
    PostDetail getPost(UUID postId, UUID userId, boolean isAdmin);
}
