package io.peach.rpc.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RetryBudgetTest {

    @Test
    void shouldBoundRetriesByBudget() {
        RpcClientResilienceOptions options =
                new RpcClientResilienceOptions(
                        2,
                        0.5d,
                        1,
                        2,
                        Duration.ZERO,
                        Duration.ZERO,
                        3,
                        Duration.ofSeconds(1),
                        3,
                        Duration.ofSeconds(1));
        RetryBudget budget = new RetryBudget(options);

        assertTrue(budget.tryAcquireRetry());
        assertFalse(budget.tryAcquireRetry());

        budget.onRequest();
        assertFalse(budget.tryAcquireRetry());
        budget.onRequest();
        assertTrue(budget.tryAcquireRetry());
    }
}
