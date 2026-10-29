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

    /**
     * 글 행을 잠그고 읽는다(트랜잭션 안에서만 의미가 있다). is_active와 작성자 탈퇴 여부를 거르지 않으므로
     * 호출하는 쪽이 직접 확인한다. 심사 완료와 삭제처럼 점수를 바꾸는 경로가 같은 글에서 겹치지 않게 한다.
     */
    Optional<Post> findByIdForUpdate(UUID postId);

    Post save(Post post);

    Board getBoard(Long boardId);

    Users getUser(UUID userId);
}
