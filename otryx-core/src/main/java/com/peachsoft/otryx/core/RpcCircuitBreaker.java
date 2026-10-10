package com.peachsoft.otryx.core;

import com.peachsoft.otryx.observability.RpcCircuitState;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 方法级熔断器，使用调用许可的代际防止过期响应覆盖新的熔断状态。
 *
 * <p>OPEN 到期后只允许一个 HALF_OPEN 探测。熔断状态切换会更新代际，
 * 因此旧调用的成功、失败及取消回调不能改变新一轮探测结果。
 *
 * @Author Ryan
 * @Version 1.0.0
 * @CreateTime 2026/9/24 11:33
 */
final class RpcCircuitBreaker {

    static final long REJECTED = -1L;

    private final int failureThreshold;
    private final long openNanos;
    private final AtomicReference<Snapshot> snapshot =
            new AtomicReference<>(new Snapshot(
                    RpcCircuitState.CLOSED, 1L, 0, 0L, false));

    RpcCircuitBreaker(int failureThreshold, Duration openDuration) {
        this.failureThreshold = failureThreshold;
        this.openNanos = openDuration.toNanos();
    }

    /**
     * 获取调用许可的状态代际，返回值必须传给相应的终态回调。
     *
     * @return 非负代际，或被熔断时的 {@link #REJECTED}
     */
    long tryAcquire() {
        for (;;) {
            Snapshot current = snapshot.get();
            if (current.phase() == RpcCircuitState.CLOSED) {
                return current.generation();
            }
            if (current.phase() == RpcCircuitState.OPEN) {
                if (System.nanoTime() - current.openUntilNanos() < 0L) {
                    return REJECTED;
                }
                Snapshot probing = new Snapshot(
                        RpcCircuitState.HALF_OPEN,
                        current.generation() + 1L,
                        0,
                        0L,
                        true);
                if (snapshot.compareAndSet(current, probing)) {
                    return probing.generation();
                }
                continue;
            }
            if (current.probeInFlight()) {
                return REJECTED;
            }
            Snapshot probing = new Snapshot(
                    RpcCircuitState.HALF_OPEN,
                    current.generation() + 1L,
                    0,
                    0L,
                    true);
            if (snapshot.compareAndSet(current, probing)) {
                return probing.generation();
            }
        }
    }

    void onSuccess(long generation) {
        if (generation == REJECTED) {
            return;
        }
        for (;;) {
            Snapshot current = snapshot.get();
            if (current.generation() != generation
                    || current.phase() == RpcCircuitState.OPEN) {
                return;
            }
            if (current.phase() == RpcCircuitState.CLOSED
                    && current.consecutiveFailures() == 0) {
                return;
            }
            Snapshot next = new Snapshot(
                    RpcCircuitState.CLOSED,
                    current.phase() == RpcCircuitState.HALF_OPEN
                            ? current.generation() + 1L
                            : current.generation(),
                    0,
                    0L,
                    false);
            if (snapshot.compareAndSet(current, next)) {
                return;
            }
        }
    }

    void onFailure(long generation) {
        if (generation == REJECTED) {
            return;
        }
        for (;;) {
            Snapshot current = snapshot.get();
            if (current.generation() != generation
                    || current.phase() == RpcCircuitState.OPEN) {
                return;
            }
            int failures = current.consecutiveFailures() + 1;
            boolean opening =
                    current.phase() == RpcCircuitState.HALF_OPEN
                            || failures >= failureThreshold;
            Snapshot next = opening
                    ? new Snapshot(
                            RpcCircuitState.OPEN,
                            current.generation() + 1L,
                            0,
                            System.nanoTime() + openNanos,
                            false)
                    : new Snapshot(
                            RpcCircuitState.CLOSED,
                            current.generation(),
                            failures,
                            0L,
                            false);
            if (snapshot.compareAndSet(current, next)) {
                return;
            }
        }
    }

    void onCancelled(long generation) {
        if (generation == REJECTED) {
            return;
        }
        for (;;) {
            Snapshot current = snapshot.get();
            if (current.generation() != generation
                    || current.phase() != RpcCircuitState.HALF_OPEN
                    || !current.probeInFlight()) {
                return;
            }
            Snapshot next = new Snapshot(
                    RpcCircuitState.HALF_OPEN,
                    current.generation() + 1L,
                    0,
                    0L,
                    false);
            if (snapshot.compareAndSet(current, next)) {
                return;
            }
        }
    }

    boolean isOpen() {
        Snapshot current = snapshot.get();
        return current.phase() == RpcCircuitState.OPEN
                && System.nanoTime() - current.openUntilNanos() < 0L;
    }

    RpcCircuitState state() {
        return snapshot.get().phase();
    }

    private record Snapshot(
            RpcCircuitState phase,
            long generation,
            int consecutiveFailures,
            long openUntilNanos,
            boolean probeInFlight) {
    }
}
