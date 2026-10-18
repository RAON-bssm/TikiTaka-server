package com.raon.tikitaka.global.guard;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestGuardTest {

    private final RequestGuard guard = new RequestGuard();

    @Test
    void 같은_키의_두번째_요청은_409로_거절되고_첫_요청은_정상_실행된다() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = pool.submit(() -> guard.execute("k", "처리 중", () -> {
                executions.incrementAndGet();
                firstStarted.countDown();
                await(releaseFirst);
                return "first";
            }));
            assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> second = pool.submit(() -> guard.execute("k", "처리 중", () -> {
                executions.incrementAndGet();
                return "second";
            }));

            assertThatThrownBy(() -> second.get(5, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> {
                        ResponseStatusException cause = (ResponseStatusException) e.getCause();
                        assertThat(cause.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(cause.getReason()).isEqualTo("처리 중");
                    });

            releaseFirst.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo("first");
            assertThat(executions).hasValue(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void 다른_키는_동시에_실행된다() throws Exception {
        CountDownLatch bothStarted = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // 두 action이 서로가 시작하기를 기다린다. 다른 키가 막히면 latch가 풀리지 않아 실패한다
            Future<Boolean> a = pool.submit(() -> guard.execute("a", "처리 중", () -> {
                bothStarted.countDown();
                return awaitResult(bothStarted);
            }));
            Future<Boolean> b = pool.submit(() -> guard.execute("b", "처리 중", () -> {
                bothStarted.countDown();
                return awaitResult(bothStarted);
            }));

            assertThat(a.get(5, TimeUnit.SECONDS)).isTrue();
            assertThat(b.get(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void action에서_예외가_나도_키가_해제되어_다음_요청이_통과한다() {
        assertThatThrownBy(() -> guard.execute("k", "처리 중", () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(guard.execute("k", "처리 중", () -> "ok")).isEqualTo("ok");
    }

    @Test
    void 정상_종료_후에도_키가_해제된다() {
        assertThat(guard.execute("k", "처리 중", () -> 1)).isEqualTo(1);
        assertThat(guard.execute("k", "처리 중", () -> 2)).isEqualTo(2);
        assertThat(guard.runIfIdle("k", () -> { })).isTrue();
        assertThat(guard.runIfIdle("k", () -> { })).isTrue();
    }

    @Test
    void runIfIdle은_처리_중인_키면_실행하지_않고_false를_돌려준다() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger();

        CompletableFuture<Boolean> first = CompletableFuture.supplyAsync(() -> guard.runIfIdle("k", () -> {
            executions.incrementAndGet();
            started.countDown();
            await(release);
        }));
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(guard.runIfIdle("k", executions::incrementAndGet)).isFalse();

        release.countDown();
        assertThat(first.get(5, TimeUnit.SECONDS)).isTrue();
        assertThat(executions).hasValue(1);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static boolean awaitResult(CountDownLatch latch) {
        await(latch);
        return true;
    }
}
