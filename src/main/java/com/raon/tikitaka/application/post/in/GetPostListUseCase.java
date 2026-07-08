package com.raon.tikitaka.application.post.in;

import com.raon.tikitaka.domain.post.Post;

import java.util.List;

public interface GetPostListUseCase {

    List<Post> getPosts(Long boardId);
}
