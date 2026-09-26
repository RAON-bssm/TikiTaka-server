package com.raon.tikitaka.application.post.in;

import com.raon.tikitaka.application.post.PostSummary;

import java.util.List;

public interface GetPostListUseCase {

    List<PostSummary> getPosts(Long boardId);
}
