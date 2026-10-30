package com.raon.tikitaka.adapter.scheduler;

import com.raon.tikitaka.application.post.in.ProcessPostReviewUseCase;
import com.raon.tikitaka.application.post.review.ClaimedReview;
import com.raon.tikitaka.application.post.review.PostReviewResult;
import com.raon.tikitaka.global.config.PostReviewProperties;
import com.raon.tikitaka.global.guard.AiCallRateLimiter;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 게시물 AI 심사 워커. post 테이블의 PENDING 행을 대기열로 쓰고, 2초마다 비어 있는 자리만큼 가져가 심사한다.
 *
 * 한 틱의 순서
 * 1. 빈 자리(동시 심사 수 - 처리 중) 만큼 호출 속도 제한기에서 허가를 받는다. 허가가 없으면 선점하지 않는다
 *    (먼저 선점해 두면 기다리는 사이 선점 시각이 오래돼 다른 틱이 다시 가져간다)
 * 2. 받은 허가 수만큼 선점하고, 글이 모자라 남은 허가는 돌려준다
 * 3. 선점한 글을 전용 스레드풀에 넘긴다. AI를 부르지 않고 끝난 글(시즌 확정, 이미지 오류)의 허가도 돌려준다
 *
 * 공용 @Async 풀은 쓰지 않는다. 다른 비동기 작업(메일 등)과 섞이면 심사가 밀리거나 반대로 다른 작업을 막는다.
 * post-review.async가 false여도 워커는 돈다. 동기 방식에서는 PENDING이 생기지 않아 틱마다 count 한 번으로 끝나고,
 * 관리자 재심사나 플래그를 끈 직후 남은 PENDING도 처리된다.
 * 서버가 처리 도중 죽어도 선점 시각이 claim-timeout을 넘기면 다시 선점되므로 복구 스케줄러가 따로 없다.
 */
@Slf4j
@Component
public class PostReviewWorker {

    private final ProcessPostReviewUseCase processPostReviewUseCase;
    private final AiCallRateLimiter rateLimiter;
    private final ExecutorService executor;
    private final int concurrency;
    private final Semaphore slots;
    private final AtomicInteger threadNumber = new AtomicInteger();

    @Autowired
    public PostReviewWorker(ProcessPostReviewUseCase processPostReviewUseCase, AiCallRateLimiter rateLimiter,
                            PostReviewProperties properties) {
        this.processPostReviewUseCase = processPostReviewUseCase;
        this.rateLimiter = rateLimiter;
        this.concurrency = properties.concurrency();
        this.slots = new Semaphore(properties.concurrency());
        this.executor = Executors.newFixedThreadPool(properties.concurrency(), runnable -> {
            Thread thread = new Thread(runnable, "post-review-" + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    /** 테스트에서 실행기를 바꿔 끼우는 용도 */
    PostReviewWorker(ProcessPostReviewUseCase processPostReviewUseCase, AiCallRateLimiter rateLimiter,
                     int concurrency, ExecutorService executor) {
        this.processPostReviewUseCase = processPostReviewUseCase;
        this.rateLimiter = rateLimiter;
        this.concurrency = concurrency;
        this.slots = new Semaphore(concurrency);
        this.executor = executor;
    }

    @Scheduled(fixedDelay = 2000)
    public void tick() {
        try {
            // 백오프 중이거나 심사 중인 글은 세지 않는다. 선점할 글이 없는 틱은 조회 한 번으로 조용히 끝난다
            long claimable = processPostReviewUseCase.countClaimable();
            if (claimable == 0) {
                return;
            }

            int free = slots.availablePermits();
            if (free == 0) {
                return;
            }
            AiCallRateLimiter.Permits permits = rateLimiter.acquire((int) Math.min(free, claimable));
            if (permits.count() == 0) {
                return;     // 최근 60초 호출 한도를 다 썼다. 선점하지 않으므로 시도 횟수도 늘지 않는다
            }

            List<ClaimedReview> claimed;
            try {
                claimed = processPostReviewUseCase.claim(permits.count());
            } catch (RuntimeException e) {
                rateLimiter.release(permits.grantedAt(), permits.count());
                throw e;
            }
            if (claimed.size() < permits.count()) {
                // 다른 틱이 먼저 잡았거나 그 사이 상태가 바뀌어 덜 가져왔다
                rateLimiter.release(permits.grantedAt(), permits.count() - claimed.size());
            }
            if (!claimed.isEmpty()) {
                log.info("게시물 심사 선점: claimed={}, claimable={}, inFlight={}",
                        claimed.size(), claimable, concurrency - slots.availablePermits());
            }
            for (ClaimedReview review : claimed) {
                submit(review, permits.grantedAt());
            }
        } catch (Exception e) {
            log.error("게시물 심사 워커 틱 실패", e);
        }
    }

    private void submit(ClaimedReview review, Instant grantedAt) {
        // 빈 자리만큼만 선점했으므로 바로 잡힌다
        slots.acquireUninterruptibly();
        try {
            executor.execute(() -> {
                try {
                    PostReviewResult result = processPostReviewUseCase.review(review);
                    if (!result.aiCalled()) {
                        rateLimiter.release(grantedAt, 1);
                    }
                } catch (Exception e) {
                    // 한 건의 실패가 워커를 멈추지 않게 한다. 상태는 PENDING으로 남고 선점 만료 뒤 다시 처리된다
                    log.error("게시물 심사 처리 실패: postId={}", review.postId(), e);
                } finally {
                    slots.release();
                }
            });
        } catch (RuntimeException e) {
            slots.release();
            rateLimiter.release(grantedAt, 1);
            log.error("게시물 심사 작업 제출 실패: postId={}", review.postId(), e);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
