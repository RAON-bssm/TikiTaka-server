package com.raon.tikitaka.adapter.post.out;

import com.raon.tikitaka.adapter.board.out.BoardJpaRepository;
import com.raon.tikitaka.adapter.user.out.UserJpaRepository;
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

    @Override
    public List<Post> findAllActiveByBoardId(Long boardId) {
        return postJpaRepository.findAllActiveByBoardId(boardId);
    }

    @Override
    public List<Post> findAllActiveByBoardIdExcludingBlockedBy(Long boardId, UUID viewerId) {
        return postJpaRepository.findAllActiveByBoardIdExcludingBlockedBy(boardId, viewerId);
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
