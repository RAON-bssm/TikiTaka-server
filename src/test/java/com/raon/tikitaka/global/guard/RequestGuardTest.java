package com.raon.tikitaka.global.guard;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
        assertThat(guard.executeWaiting("k", "처리 중", () -> 3)).isEqualTo(3);
        assertThat(guard.trackedKeyCount()).isZero();
    }

    @Test
    void executeWaiting은_앞선_요청이_끝난_뒤_먼저_온_순서대로_실행한다() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            Future<?> first = pool.submit(() -> guard.executeWaiting("k", "처리 중", () -> {
                firstStarted.countDown();
                await(releaseFirst);
                order.add("first");
            }));
            assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> second = pool.submit(() -> guard.executeWaiting("k", "처리 중", () -> order.add("second")));
            // second가 락 대기열에 들어간 뒤에 third가 도착하도록 순서를 만든다
            waitUntilQueued(guard, "k", 1);
            Future<?> third = pool.submit(() -> guard.executeWaiting("k", "처리 중", () -> order.add("third")));
            waitUntilQueued(guard, "k", 2);

            // 앞선 요청이 끝나기 전에는 뒤 요청이 실행되지 않는다
            assertThat(order).isEmpty();

            releaseFirst.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            third.get(5, TimeUnit.SECONDS);

            assertThat(order).containsExactly("first", "second", "third");
            assertThat(guard.trackedKeyCount()).isZero();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void executeWaiting이_기다리는_동안_execute는_바로_409로_거절된다() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> first = CompletableFuture.runAsync(() -> guard.executeWaiting("k", "처리 중", () -> {
            started.countDown();
            await(release);
        }));
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> guard.execute("k", "처리 중", () -> "x"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        release.countDown();
        first.get(5, TimeUnit.SECONDS);
    }

    /**
     * 거절은 다른 스레드에서 해야 한다. ReentrantLock은 재진입이 되므로 락을 쥔 스레드가
     * 같은 키로 다시 execute하면 거절되지 않고 그대로 실행된다.
     */
    @Test
    void 거절된_뒤에도_키가_정리된다() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> guard.execute("k", "처리 중", () -> {
            started.countDown();
            await(release);
        }));
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> guard.execute("k", "처리 중", () -> 1))
                .isInstanceOf(ResponseStatusException.class);

        release.countDown();
        holder.get(5, TimeUnit.SECONDS);
        assertThat(guard.trackedKeyCount()).isZero();
    }

    /**
     * key의 락 대기열에 expected개가 들어갈 때까지 기다린다. 고정 sleep을 쓰면 느린 환경에서
     * 대기열 진입 전에 다음 단계로 넘어가 순서 검증이 가끔 깨진다.
     */
    private static void waitUntilQueued(RequestGuard guard, String key, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (guard.queuedCount(key) < expected) {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("대기열 진입 timeout: " + key);
            }
            Thread.sleep(5);
        }
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
