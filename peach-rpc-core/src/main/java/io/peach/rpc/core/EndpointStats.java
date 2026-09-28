package io.peach.rpc.core;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** 单端点负载均衡与异常实例剔除统计。 */
final class EndpointStats {

    private final AtomicInteger inflight = new AtomicInteger();
    private final AtomicLong ewmaNanos = new AtomicLong(1_000_000);
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong ejectedUntilNanos = new AtomicLong();

    int inflight() {
        return inflight.get();
    }

    long ewma() {
        return ewmaNanos.get();
    }

    boolean available() {
        return System.nanoTime() >= ejectedUntilNanos.get();
    }

    void begin() {
        inflight.incrementAndGet();
    }

    void endSuccess(long nanos) {
        end(nanos);
        consecutiveFailures.set(0);
    }

    void endCancelled(long nanos) {
        end(nanos);
    }

    void endFailure(long nanos, RpcClientResilienceOptions options) {
        end(nanos);
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= options.outlierConsecutiveFailureThreshold()) {
            consecutiveFailures.set(0);
            ejectedUntilNanos.set(
                    System.nanoTime() + options.outlierEjectionDuration().toNanos());
        }
    }

    private void end(long nanos) {
        inflight.decrementAndGet();
        ewmaNanos.updateAndGet(previous ->
                previous + (nanos - previous) / 8);
    }
}
