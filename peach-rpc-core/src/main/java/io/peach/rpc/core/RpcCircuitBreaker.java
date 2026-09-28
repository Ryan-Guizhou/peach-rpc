package io.peach.rpc.core;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** 方法级连续基础设施失败熔断器，OPEN 到期后只允许一个 HALF_OPEN 探测。 */
final class RpcCircuitBreaker {
    private final int failureThreshold;
    private final long openNanos;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong openUntilNanos = new AtomicLong();
    private final AtomicBoolean halfOpenProbe = new AtomicBoolean();

    RpcCircuitBreaker(int failureThreshold, Duration openDuration) {
        this.failureThreshold = failureThreshold;
        this.openNanos = openDuration.toNanos();
    }

    boolean tryAcquire() {
        long openUntil = openUntilNanos.get();
        if (openUntil == 0L) {
            return true;
        }
        if (System.nanoTime() < openUntil) {
            return false;
        }
        return halfOpenProbe.compareAndSet(false, true);
    }

    void onSuccess() {
        consecutiveFailures.set(0);
        openUntilNanos.set(0L);
        halfOpenProbe.set(false);
    }

    void onFailure() {
        boolean halfOpen = halfOpenProbe.getAndSet(false);
        int failures = consecutiveFailures.incrementAndGet();
        if (halfOpen || failures >= failureThreshold) {
            consecutiveFailures.set(0);
            openUntilNanos.set(System.nanoTime() + openNanos);
        }
    }

    void onCancelled() {
        halfOpenProbe.set(false);
    }

    boolean isOpen() {
        long openUntil = openUntilNanos.get();
        return openUntil != 0L && System.nanoTime() < openUntil;
    }
}
