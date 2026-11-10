package com.raon.tikitaka.application.post.in;

import com.raon.tikitaka.application.post.PostPage;
import com.raon.tikitaka.application.post.PostSummary;

import java.util.List;
import java.util.UUID;

public interface GetPostListUseCase {

    /**
     * userId는 비로그인 조회(GET /api/post/**는 permitAll) 시 null일 수 있고,
     * 그 경우 각 게시물의 likedByMe는 항상 false로 내려간다.
     */
    List<PostSummary> getPosts(Long boardId, UUID userId);

    /**
     * getPosts의 커서 페이징 버전. 순서와 필터는 getPosts와 같다.
     * size가 null이면 기본값(20)이고 1~50을 벗어나면 400이다. cursor가 null이거나 비어 있으면 첫 페이지이고,
     * 서버가 내려준 값이 아니면 400이다.
     */
    PostPage getPostPage(Long boardId, UUID userId, Integer size, String cursor);
}
