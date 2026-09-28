package com.raon.tikitaka.application.post.in;

import com.raon.tikitaka.application.post.PostSummary;

import java.util.List;
import java.util.UUID;

public interface GetPostListUseCase {

    /**
     * userId는 비로그인 조회(GET /api/post/**는 permitAll) 시 null일 수 있고,
     * 그 경우 각 게시물의 likedByMe는 항상 false로 내려간다.
     */
    List<PostSummary> getPosts(Long boardId, UUID userId);
}
