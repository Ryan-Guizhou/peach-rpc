package com.peachsoft.otryx.core;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Consumer 全局重试预算，使用定点额度限制故障期间的重试放大。
 *
 * @Author Ryan
 * @Version 1.0.0
 * @CreateTime 2026/9/24 11:33
 */
final class RetryBudget {
    private static final long SCALE = 1_000_000L;

    private final long refillPerRequest;
    private final long maxCredits;
    private final AtomicLong credits;

    RetryBudget(RpcClientResilienceOptions options) {
        refillPerRequest = Math.round(options.retryBudgetRatio() * SCALE);
        maxCredits = options.retryBudgetMaxRetries() * SCALE;
        credits = new AtomicLong(options.retryBudgetMinRetries() * SCALE);
    }

    void onRequest() {
        if (refillPerRequest == 0L || maxCredits == 0L) {
            return;
        }
        for (;;) {
            long current = credits.get();
            if (current >= maxCredits) {
                return;
            }
            long next = Math.min(
                    maxCredits,
                    current + refillPerRequest);
            if (credits.compareAndSet(current, next)) {
                return;
            }
        }
    }

    boolean tryAcquireRetry() {
        for (;;) {
            long current = credits.get();
            if (current < SCALE) {
                return false;
            }
            if (credits.compareAndSet(current, current - SCALE)) {
                return true;
            }
        }
    }

    long availableRetries() {
        return credits.get() / SCALE;
    }
}
