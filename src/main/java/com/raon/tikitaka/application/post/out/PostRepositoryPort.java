package com.raon.tikitaka.application.post.out;

import com.raon.tikitaka.domain.board.Board;
import com.raon.tikitaka.domain.post.Post;
import com.raon.tikitaka.domain.user.Users;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostRepositoryPort {

    List<Post> findAllActiveByBoardId(Long boardId);

    Optional<Post> findActiveById(UUID postId);

    Post save(Post post);

    Board getBoard(Long boardId);

    Users getUser(UUID userId);
}
