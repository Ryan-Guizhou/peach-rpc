package io.peach.rpc.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.peach.rpc.observability.RpcCircuitState;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RpcCircuitBreakerTest {

    @Test
    void shouldOpenAfterConsecutiveFailures() {
        RpcCircuitBreaker breaker =
                new RpcCircuitBreaker(2, Duration.ofSeconds(1));

        assertTrue(breaker.tryAcquire());
        breaker.onFailure();
        assertTrue(breaker.tryAcquire());
        breaker.onFailure();

        assertTrue(breaker.isOpen());
        assertFalse(breaker.tryAcquire());
    }

    @Test
    void halfOpenShouldAllowOnlyOneConcurrentProbe()
            throws Exception {
        RpcCircuitBreaker breaker =
                new RpcCircuitBreaker(
                        1,
                        Duration.ofMillis(20));

        assertTrue(breaker.tryAcquire());
        breaker.onFailure();
        Thread.sleep(30L);

        int concurrency = 16;
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger acquired = new AtomicInteger();
        try (var executor =
                     Executors.newFixedThreadPool(
                             concurrency)) {
            List<Future<?>> futures =
                    new ArrayList<>();
            for (int index = 0;
                    index < concurrency;
                    index++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    if (breaker.tryAcquire()) {
                        acquired.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        }

        assertEquals(1, acquired.get());
    }

    @Test
    void halfOpenShouldAllowOnlyOneConcurrentProbe()
            throws Exception {
        RpcCircuitBreaker breaker =
                new RpcCircuitBreaker(
                        1,
                        Duration.ofMillis(20));

        breaker.onFailure();
        assertTrue(breaker.isOpen());
        Thread.sleep(30L);

        int contenders = 32;
        CountDownLatch ready =
                new CountDownLatch(contenders);
        CountDownLatch start =
                new CountDownLatch(1);
        List<CompletableFuture<Boolean>> probes =
                new ArrayList<>();

        for (int index = 0;
                index < contenders;
                index++) {
            probes.add(CompletableFuture.supplyAsync(() -> {
                ready.countDown();
                try {
                    if (!start.await(
                            2,
                            TimeUnit.SECONDS)) {
                        throw new AssertionError(
                                "Probe start barrier timed out");
                    }
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(error);
                }
                return breaker.tryAcquire();
            }));
        }

        assertTrue(ready.await(2, TimeUnit.SECONDS));
        start.countDown();

        long accepted = probes.stream()
                .map(CompletableFuture::join)
                .filter(Boolean::booleanValue)
                .count();

        assertEquals(1L, accepted);
        assertEquals(
                RpcCircuitState.HALF_OPEN,
                breaker.state());

        breaker.onSuccess();

        assertEquals(
                RpcCircuitState.CLOSED,
                breaker.state());
        assertTrue(breaker.tryAcquire());
    }

    @Test
    void successShouldResetFailures() {
        RpcCircuitBreaker breaker =
                new RpcCircuitBreaker(2, Duration.ofSeconds(1));

        breaker.onFailure();
        breaker.onSuccess();
        breaker.onFailure();

        assertFalse(breaker.isOpen());
        assertTrue(breaker.tryAcquire());
    }
}
