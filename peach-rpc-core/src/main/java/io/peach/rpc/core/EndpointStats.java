package io.peach.rpc.core;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** 单端点负载均衡统计。 */
final class EndpointStats {

    private final AtomicInteger inflight = new AtomicInteger();
    private final AtomicLong ewmaNanos = new AtomicLong(1_000_000);

    int inflight() {
        return inflight.get();
    }

    long ewma() {
        return ewmaNanos.get();
    }

    void begin() {
        inflight.incrementAndGet();
    }

    void end(long nanos) {
        inflight.decrementAndGet();
        ewmaNanos.updateAndGet(previous -> previous + (nanos - previous) / 8);
    }
}
