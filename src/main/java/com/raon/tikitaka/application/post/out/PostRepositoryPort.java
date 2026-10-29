package com.raon.tikitaka.application.post.out;

import com.raon.tikitaka.domain.board.Board;
import com.raon.tikitaka.domain.post.Post;
import com.raon.tikitaka.domain.post.PostStatus;
import com.raon.tikitaka.domain.user.Users;

import java.time.LocalDateTime;
import java.util.Collection;
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

    /**
     * 사용자가 since 이후에 만든 글 중 statuses에 해당하는 글의 작성 시각을 최근 순으로 최대 limit건 돌려준다.
     * 삭제한 글(is_active = false)도 포함한다. 업로드 횟수 한도에 쓴다.
     */
    List<LocalDateTime> findRecentUploadTimes(UUID userId, LocalDateTime since, Collection<PostStatus> statuses,
                                              int limit);

    /** 사용자의 삭제하지 않은 PENDING 글 수. 심사 대기 한도에 쓴다 */
    long countActivePendingByUserId(UUID userId);

    Post save(Post post);

    Board getBoard(Long boardId);

    Users getUser(UUID userId);
}
