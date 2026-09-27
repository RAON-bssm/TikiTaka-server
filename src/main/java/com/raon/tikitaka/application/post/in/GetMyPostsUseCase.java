package com.raon.tikitaka.application.post.in;

import com.raon.tikitaka.domain.post.Post;

import java.util.List;
import java.util.UUID;

public interface GetMyPostsUseCase {

    /**
     * 내가 쓴 게시물 전체 목록(삭제된 글 제외). 여러 라운드·게시판을 넘나드는 목록이라
     * 최신순으로 내려주고 어느 게시판·미션에서 쓴 글인지도 함께 포함한다.
     */
    List<Post> getMyPosts(UUID userId);
}
