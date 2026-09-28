package io.peach.rpc.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
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
