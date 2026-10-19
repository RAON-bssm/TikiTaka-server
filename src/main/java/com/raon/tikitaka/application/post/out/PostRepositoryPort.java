package com.raon.tikitaka.application.post.out;

import com.raon.tikitaka.domain.board.Board;
import com.raon.tikitaka.domain.post.Post;
import com.raon.tikitaka.domain.user.Users;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostRepositoryPort {

    List<Post> findAllActiveByBoardId(Long boardId);

    /**
     * 게시판의 게시물 중 viewerId가 차단한 유저의 글을 뺀 목록. viewerId는 null이면 안 된다.
     */
    List<Post> findAllActiveByBoardIdExcludingBlockedBy(Long boardId, UUID viewerId);

    List<Post> findAllActiveByUserId(UUID userId);

    Optional<Post> findActiveById(UUID postId);

    Post save(Post post);

    Board getBoard(Long boardId);

    Users getUser(UUID userId);
}
