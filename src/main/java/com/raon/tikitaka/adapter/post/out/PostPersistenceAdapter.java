package com.raon.tikitaka.adapter.post.out;

import com.raon.tikitaka.adapter.board.out.BoardJpaRepository;
import com.raon.tikitaka.adapter.user.out.UserJpaRepository;
import com.raon.tikitaka.application.post.PostCursor;
import com.raon.tikitaka.application.post.out.PostRepositoryPort;
import com.raon.tikitaka.domain.board.Board;
import com.raon.tikitaka.domain.post.Post;
import com.raon.tikitaka.domain.post.PostStatus;
import com.raon.tikitaka.domain.user.Users;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PostPersistenceAdapter implements PostRepositoryPort {

    private final PostJpaRepository postJpaRepository;
    private final BoardJpaRepository boardJpaRepository;
    private final UserJpaRepository userJpaRepository;

    /**
     * 첫 페이지용 커서. 모든 글보다 뒤라서 (createdAt < 이 값) 조건이 항상 참이 된다.
     * 커서 파라미터에 null을 넘기면 PostgreSQL이 타입을 추론하지 못하는 경우가 있어 값이 있는 센티널을 쓴다.
     */
    private static final PostCursor FIRST_PAGE = new PostCursor(LocalDateTime.of(9999, 12, 31, 23, 59, 59),
            new UUID(-1L, -1L));

    @Override
    public List<Post> findAllActiveByBoardId(Long boardId) {
        return postJpaRepository.findAllActiveByBoardId(boardId);
    }

    @Override
    public List<Post> findAllActiveByBoardIdExcludingBlockedBy(Long boardId, UUID viewerId) {
        return postJpaRepository.findAllActiveByBoardIdExcludingBlockedBy(boardId, viewerId);
    }

    @Override
    public List<Post> findActivePageByBoardId(Long boardId, PostCursor after, int limit) {
        PostCursor cursor = after != null ? after : FIRST_PAGE;
        return postJpaRepository.findActivePageByBoardId(boardId, cursor.createdAt(), cursor.postId(), Limit.of(limit));
    }

    @Override
    public List<Post> findActivePageByBoardIdExcludingBlockedBy(Long boardId, UUID viewerId, PostCursor after, int limit) {
        PostCursor cursor = after != null ? after : FIRST_PAGE;
        return postJpaRepository.findActivePageByBoardIdExcludingBlockedBy(boardId, viewerId, cursor.createdAt(),
                cursor.postId(), Limit.of(limit));
    }

    @Override
    public Optional<Post> findActiveById(UUID postId) {
        return postJpaRepository.findActiveById(postId);
    }

    @Override
    public Optional<Post> findByIdForUpdate(UUID postId) {
        return postJpaRepository.findByIdForUpdate(postId);
    }

    @Override
    public List<Post> findAllActiveByUserId(UUID userId) {
        return postJpaRepository.findAllActiveByUserId(userId);
    }

    @Override
    public List<LocalDateTime> findRecentUploadTimes(UUID userId, LocalDateTime since,
                                                     Collection<PostStatus> statuses, int limit) {
        return postJpaRepository.findRecentUploadTimes(userId, since, statuses, Limit.of(limit));
    }

    @Override
    public long countActivePendingByUserId(UUID userId) {
        return postJpaRepository.countActivePendingByUserId(userId);
    }

    @Override
    public long countActivePending() {
        return postJpaRepository.countActivePending();
    }

    @Override
    public Post save(Post post) {
        return postJpaRepository.save(post);
    }

    @Override
    public Board getBoard(Long boardId) {
        // 점수 계산이 match, team1, team2, stage를 전부 쓰므로 그래프째 즉시 로딩한다
        Optional<Board> board = boardJpaRepository.findByIdWithMatchGraph(boardId);
        if (board.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "게시판을 찾을 수 없습니다.");
        }
        return board.get();
    }

    @Override
    public Users getUser(UUID userId) {
        Optional<Users> user = userJpaRepository.findByIdWithLocations(userId);
        if (user.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다.");
        }
        return user.get();
    }
}
