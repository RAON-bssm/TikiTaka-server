package com.raon.tikitaka.application.post.review;

import com.raon.tikitaka.application.post.in.CompletePostReviewUseCase;
import com.raon.tikitaka.application.post.in.ProcessPostReviewUseCase;
import com.raon.tikitaka.application.ranking.out.SeasonResultRepositoryPort;
import com.raon.tikitaka.application.review.AiReviewRejectedException;
import com.raon.tikitaka.application.review.AiReviewResult;
import com.raon.tikitaka.application.review.AiReviewRetryableException;
import com.raon.tikitaka.application.review.in.ReviewUseCase;
import com.raon.tikitaka.application.storage.StoredImage;
import com.raon.tikitaka.application.storage.out.StoragePort;
import com.raon.tikitaka.domain.post.PostStatus;
import com.raon.tikitaka.global.guard.RequestGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLConnection;
import java.time.Duration;
import java.util.List;

/**
 * 게시물 AI 심사 한 건 처리. 트랜잭션 밖에서 S3 이미지를 읽고 AI를 부른 뒤, 결과에 따라
 * 심사 완료(PostService.completeReview) 또는 대기열 상태 변경(PostReviewQueueService)을 짧은 트랜잭션으로 한다.
 *
 * | 결과 | 처리 |
 * | 점수 0~100 | completeReview -> DONE |
 * | 429, 5xx, 타임아웃, 네트워크, 응답 형식 오류 | 시도 횟수 +1, 백오프 후 재시도, 한도에 닿으면 FAILED |
 * | 429를 뺀 4xx | REJECTED, 재시도하지 않음 |
 * | 시즌이 이미 확정됨 | AI를 부르지 않고 EXPIRED |
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostReviewProcessor implements ProcessPostReviewUseCase {

    /** 저장소가 content-type을 주지 않고 파일 이름으로도 알 수 없을 때 쓰는 값 */
    private static final String DEFAULT_IMAGE_TYPE = "image/jpeg";

    private final PostReviewQueueService postReviewQueueService;
    private final CompletePostReviewUseCase completePostReviewUseCase;
    private final ReviewUseCase reviewUseCase;
    private final StoragePort storagePort;
    private final SeasonResultRepositoryPort seasonResultRepositoryPort;
    private final RequestGuard requestGuard;

    @Override
    public long countPending() {
        return postReviewQueueService.countPending();
    }

    @Override
    public List<ClaimedReview> claim(int limit) {
        return postReviewQueueService.claim(limit);
    }

    @Override
    public PostReviewResult review(ClaimedReview claimed) {
        // 확정된 시즌에는 점수를 더할 수 없다. 확정 배치가 PENDING을 EXPIRED로 바꾸지만,
        // 그 뒤에 선점된 글(확정 직전 선점 등)이 있을 수 있어 AI를 부르기 전에 한 번 더 본다
        if (seasonResultRepositoryPort.existsBySeason(claimed.season())) {
            boolean expired = postReviewQueueService.finish(claimed.postId(), PostStatus.EXPIRED);
            return logged(claimed, expired ? ReviewOutcome.EXPIRED : ReviewOutcome.SKIPPED, false, 0);
        }

        StoredImage image;
        try {
            image = storagePort.download(claimed.postImage());
        } catch (RuntimeException e) {
            // S3 일시 오류일 수 있어 재시도한다. 이미지가 영영 없으면 시도 한도에서 FAILED로 끝난다
            log.warn("게시물 심사 이미지 읽기 실패: postId={}, key={}, cause={}",
                    claimed.postId(), claimed.postImage(), e.toString());
            return logged(claimed, failure(claimed, null), false, 0);
        }

        long startedAt = System.currentTimeMillis();
        AiReviewResult review;
        try {
            review = reviewUseCase.evaluate(claimed.mission(), claimed.content(), image.content(),
                    contentType(image, claimed.postImage()));
        } catch (AiReviewRejectedException e) {
            boolean rejected = postReviewQueueService.finish(claimed.postId(), PostStatus.REJECTED);
            return logged(claimed, rejected ? ReviewOutcome.REJECTED : ReviewOutcome.SKIPPED, true,
                    System.currentTimeMillis() - startedAt);
        } catch (AiReviewRetryableException e) {
            log.warn("게시물 심사 일시 오류: postId={}, cause={}", claimed.postId(), e.getMessage());
            return logged(claimed, failure(claimed, e.getRetryAfter()), true, System.currentTimeMillis() - startedAt);
        } catch (RuntimeException e) {
            // 분류되지 않은 예외도 재시도 대상으로 센다. 세지 않으면 같은 글이 끝없이 다시 선점된다
            log.error("게시물 심사 중 예상하지 못한 오류: postId={}", claimed.postId(), e);
            return logged(claimed, failure(claimed, null), true, System.currentTimeMillis() - startedAt);
        }
        long reviewMs = System.currentTimeMillis() - startedAt;

        try {
            // 포인트를 바꾸므로 상점 구매·가샤폰과 같은 포인트 키로 감싼다. 키는 트랜잭션 밖에서 잡아야 한다
            boolean applied = requestGuard.executeWaiting(RequestGuard.pointsKey(claimed.authorId()),
                    RequestGuard.POINTS_BUSY_MESSAGE,
                    () -> completePostReviewUseCase.completeReview(claimed.postId(), review.score(), review.review()));
            return logged(claimed, applied ? ReviewOutcome.DONE : ReviewOutcome.SKIPPED, true, reviewMs);
        } catch (ResponseStatusException e) {
            if (e.getStatusCode() != HttpStatus.CONFLICT) {
                log.error("게시물 심사 결과 반영 실패: postId={}", claimed.postId(), e);
                return logged(claimed, failure(claimed, null), true, reviewMs);
            }
            // 5초 안에 포인트 키 순서가 오지 않았다. 실패가 아니므로 시도 횟수를 올리지 않고 선점만 푼다.
            // 다음 틱에 다시 선점되면 AI를 한 번 더 부른다
            postReviewQueueService.release(claimed.postId());
            return logged(claimed, ReviewOutcome.DEFERRED, true, reviewMs);
        } catch (RuntimeException e) {
            // 반영 중 오류(예: 라운드의 평균 동네 인원 C 누락)는 트랜잭션이 롤백돼 PENDING으로 남는다.
            // 시도 횟수로 세지 않으면 선점 만료 때마다 AI를 다시 부르며 끝없이 돈다
            log.error("게시물 심사 결과 반영 실패: postId={}", claimed.postId(), e);
            return logged(claimed, failure(claimed, null), true, reviewMs);
        }
    }

    private ReviewOutcome failure(ClaimedReview claimed, Duration retryAfter) {
        PostStatus status = postReviewQueueService.recordFailure(claimed.postId(), retryAfter);
        if (status == PostStatus.FAILED) {
            return ReviewOutcome.FAILED;
        }
        return status == PostStatus.PENDING ? ReviewOutcome.RETRY : ReviewOutcome.SKIPPED;
    }

    private PostReviewResult logged(ClaimedReview claimed, ReviewOutcome outcome, boolean aiCalled, long reviewMs) {
        long queueWaitMs = Duration.between(claimed.createdAt(), claimed.claimedAt()).toMillis();
        log.info("게시물 심사 처리: postId={}, result={}, attempt={}, queueWaitMs={}, reviewMs={}",
                claimed.postId(), outcome, claimed.attempts() + 1, queueWaitMs, reviewMs);
        return new PostReviewResult(outcome, aiCalled);
    }

    /**
     * 업로드할 때 붙인 content-type을 저장소가 돌려준다. 없으면 파일 이름으로 추측한다.
     */
    private static String contentType(StoredImage image, String key) {
        if (image.contentType() != null && image.contentType().startsWith("image/")) {
            return image.contentType();
        }
        String guessed = URLConnection.guessContentTypeFromName(key);
        return guessed != null ? guessed : DEFAULT_IMAGE_TYPE;
    }
}
