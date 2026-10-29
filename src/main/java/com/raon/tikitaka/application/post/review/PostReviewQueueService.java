package com.raon.tikitaka.application.post.review;

import com.raon.tikitaka.application.post.in.RetryPostReviewUseCase;
import com.raon.tikitaka.application.post.out.PostRepositoryPort;
import com.raon.tikitaka.application.post.out.PostReviewQueuePort;
import com.raon.tikitaka.application.ranking.out.SeasonResultRepositoryPort;
import com.raon.tikitaka.domain.post.Post;
import com.raon.tikitaka.domain.post.PostStatus;
import com.raon.tikitaka.global.config.PostReviewProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 심사 대기열의 상태 변경. 메서드마다 짧은 트랜잭션 하나이고, 바꾸기 전에 글 행을 잠가 PENDING인지 다시 본다.
 * 심사 완료(PostService.completeReview), 삭제, 시즌 확정의 EXPIRED 처리와 같은 행 잠금을 쓰므로
 * 이미 다른 상태가 된 글을 덮어쓰지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PostReviewQueueService implements RetryPostReviewUseCase {

    private final PostRepositoryPort postRepositoryPort;
    private final PostReviewQueuePort postReviewQueuePort;
    private final PostReviewProperties properties;
    private final SeasonResultRepositoryPort seasonResultRepositoryPort;

    @Transactional(readOnly = true)
    public long countPending() {
        return postReviewQueuePort.countPending();
    }

    public List<ClaimedReview> claim(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleBefore = now.minus(properties.claimTimeout());
        List<ClaimedReview> claimed = new ArrayList<>();
        for (Post post : postReviewQueuePort.claim(limit, now, staleBefore)) {
            claimed.add(new ClaimedReview(
                    post.getPostId(),
                    post.getUserId().getUserId(),
                    post.getBoard().getBoardId(),
                    post.getBoard().getMatch().getMission(),
                    post.getBoard().getMatch().getStage().getSeason(),
                    post.getContent(),
                    post.getPostImage(),
                    post.getReviewAttempts(),
                    post.getCreatedAt(),
                    now));
        }
        return claimed;
    }

    /**
     * 실제 시도가 실패했다. 시도 횟수를 올리고, 한도에 닿으면 FAILED, 아니면 백오프 뒤에 다시 선점되게 둔다.
     * retryAfter가 있으면(Gemini가 대기 시간을 알려준 경우) 기본 백오프 대신 쓴다.
     * 이미 PENDING이 아니면 바꾸지 않고 현재 상태를 돌려준다.
     */
    public PostStatus recordFailure(UUID postId, Duration retryAfter) {
        Optional<Post> locked = postRepositoryPort.findByIdForUpdate(postId);
        if (locked.isEmpty()) {
            return null;
        }
        Post post = locked.get();
        if (!post.isPending()) {
            return post.getStatus();
        }
        int attempts = post.getReviewAttempts() + 1;
        if (attempts >= properties.maxAttempts()) {
            postReviewQueuePort.finish(postId, PostStatus.FAILED, attempts);
            return PostStatus.FAILED;
        }
        Duration wait = retryAfter != null ? retryAfter : properties.backoffAfter(attempts);
        postReviewQueuePort.scheduleRetry(postId, attempts, LocalDateTime.now().plus(wait));
        return PostStatus.PENDING;
    }

    /**
     * PENDING인 글만 status(REJECTED, EXPIRED 등)로 바꾼다. 바꿨으면 true다.
     */
    public boolean finish(UUID postId, PostStatus status) {
        Optional<Post> locked = postRepositoryPort.findByIdForUpdate(postId);
        if (locked.isEmpty() || !locked.get().isPending()) {
            return false;
        }
        postReviewQueuePort.finish(postId, status, locked.get().getReviewAttempts());
        return true;
    }

    /** 실패로 세지 않고 선점만 푼다. 다음 틱에 다시 선점된다 */
    public void release(UUID postId) {
        Optional<Post> locked = postRepositoryPort.findByIdForUpdate(postId);
        if (locked.isPresent() && locked.get().isPending()) {
            postReviewQueuePort.release(postId);
        }
    }

    /**
     * 관리자 재심사. FAILED만 받는다. REJECTED는 Gemini가 내용 때문에 거절한 글이라 다시 보내도 같고,
     * EXPIRED는 시즌이 확정돼 점수를 더할 수 없다. 탈퇴한 작성자의 글도 다른 글과 똑같이 다시 심사한다.
     * FAILED여도 글이 속한 시즌이 이미 확정됐으면 409다. 되돌려도 워커가 AI를 부르지 않고 EXPIRED로 바꾸기 때문이다.
     * 검사 순서는 없음·삭제(404), FAILED 아님(409), 시즌 확정(409)이다.
     */
    @Override
    public void retryReview(UUID postId) {
        Optional<Post> locked = postRepositoryPort.findByIdForUpdate(postId);
        if (locked.isEmpty() || !locked.get().isActive()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "게시물을 찾을 수 없습니다.");
        }
        if (locked.get().getStatus() != PostStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "심사에 실패한 글만 다시 심사할 수 있습니다.");
        }
        // 워커가 확정 여부를 볼 때와 같은 경로(글의 게시판 -> 매치 -> 라운드)로 시즌을 읽는다
        int season = locked.get().getBoard().getMatch().getStage().getSeason();
        if (seasonResultRepositoryPort.existsBySeason(season)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "시즌이 이미 확정되어 다시 심사할 수 없습니다.");
        }
        postReviewQueuePort.reset(postId);
        log.info("게시물 재심사 요청: postId={}", postId);
    }
}
