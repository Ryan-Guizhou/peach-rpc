package com.peachsoft.otryx.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.peachsoft.otryx.observability.RpcCircuitState;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * 验证熔断器故障判定、恢复与状态转换。
 *
 * @Author Ryan
 * @Version 1.0.0-SNAPSHOT
 * @CreateTime 2026/9/24 11:36
 */
class RpcCircuitBreakerTest {

    @Test
    void shouldOpenAfterConsecutiveFailures() {
        RpcCircuitBreaker breaker =
                new RpcCircuitBreaker(2, Duration.ofSeconds(1));

        breaker.onFailure(breaker.tryAcquire());
        breaker.onFailure(breaker.tryAcquire());

        assertTrue(breaker.isOpen());
        assertEquals(RpcCircuitBreaker.REJECTED, breaker.tryAcquire());
    }

    @Test
    void halfOpenShouldAllowOnlyOneConcurrentProbe()
            throws Exception {
        RpcCircuitBreaker breaker =
                new RpcCircuitBreaker(1, Duration.ofMillis(20));

        breaker.onFailure(breaker.tryAcquire());
        assertTrue(breaker.isOpen());
        Thread.sleep(30L);

        int contenders = 32;
        CountDownLatch ready = new CountDownLatch(contenders);
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Long>> probes = new ArrayList<>();

        try (var executor = Executors.newFixedThreadPool(contenders)) {
            for (int index = 0; index < contenders; index++) {
                probes.add(CompletableFuture.supplyAsync(() -> {
                    ready.countDown();
                    try {
                        if (!start.await(2, TimeUnit.SECONDS)) {
                            throw new AssertionError(
                                    "Probe start barrier timed out");
                        }
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(error);
                    }
                    return breaker.tryAcquire();
                }, executor));
            }
            assertTrue(ready.await(2, TimeUnit.SECONDS));
            start.countDown();
        }

        List<Long> acquired = probes.stream()
                .map(CompletableFuture::join)
                .filter(generation -> generation != RpcCircuitBreaker.REJECTED)
                .toList();

        assertEquals(1, acquired.size());
        assertEquals(RpcCircuitState.HALF_OPEN, breaker.state());

        breaker.onSuccess(acquired.getFirst());

        assertEquals(RpcCircuitState.CLOSED, breaker.state());
        assertNotEquals(RpcCircuitBreaker.REJECTED, breaker.tryAcquire());
    }

    @Test
    void successShouldResetFailures() {
        RpcCircuitBreaker breaker =
                new RpcCircuitBreaker(2, Duration.ofSeconds(1));

        breaker.onFailure(breaker.tryAcquire());
        breaker.onSuccess(breaker.tryAcquire());
        breaker.onFailure(breaker.tryAcquire());

        assertFalse(breaker.isOpen());
        assertNotEquals(RpcCircuitBreaker.REJECTED, breaker.tryAcquire());
    }

    @Test
    void staleSuccessCannotCloseNewOpenCircuit() {
        RpcCircuitBreaker breaker =
                new RpcCircuitBreaker(1, Duration.ofSeconds(1));

        long earlier = breaker.tryAcquire();
        long failed = breaker.tryAcquire();
        breaker.onFailure(failed);
        assertTrue(breaker.isOpen());

        breaker.onSuccess(earlier);
        assertTrue(breaker.isOpen());
        assertEquals(RpcCircuitBreaker.REJECTED, breaker.tryAcquire());
    }

    @Test
    void staleFailureCannotUndoSuccessfulHalfOpenProbe()
            throws Exception {
        RpcCircuitBreaker breaker =
                new RpcCircuitBreaker(1, Duration.ofMillis(20));

        long earlier = breaker.tryAcquire();
        breaker.onFailure(breaker.tryAcquire());
        Thread.sleep(30L);
        long probe = breaker.tryAcquire();
        assertNotEquals(RpcCircuitBreaker.REJECTED, probe);
        breaker.onSuccess(probe);
        assertEquals(RpcCircuitState.CLOSED, breaker.state());

        breaker.onFailure(earlier);
        assertEquals(RpcCircuitState.CLOSED, breaker.state());
    }

    @Test
    void cancelledHalfOpenProbeAllowsNewGeneration()
            throws Exception {
        RpcCircuitBreaker breaker =
                new RpcCircuitBreaker(1, Duration.ofMillis(20));

        breaker.onFailure(breaker.tryAcquire());
        Thread.sleep(30L);

        long abandoned = breaker.tryAcquire();
        assertNotEquals(RpcCircuitBreaker.REJECTED, abandoned);
        breaker.onCancelled(abandoned);

        long replacement = breaker.tryAcquire();
        assertNotEquals(RpcCircuitBreaker.REJECTED, replacement);
        assertNotEquals(abandoned, replacement);

        breaker.onSuccess(abandoned);
        breaker.onFailure(abandoned);
        assertEquals(RpcCircuitState.HALF_OPEN, breaker.state());

        breaker.onSuccess(replacement);
        assertEquals(RpcCircuitState.CLOSED, breaker.state());
    }
}
