package io.peach.rpc.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.peach.rpc.api.RpcStatus;
import io.peach.rpc.api.ServiceKey;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RpcObserverTest {

    @Test
    void noopShouldStayDisabled() {
        assertFalse(RpcObserver.noop().enabled());
    }

    @Test
    void compositeShouldFanOutAndIsolateFailures() {
        AtomicInteger calls = new AtomicInteger();
        RpcObserver failing = new RpcObserver() {
            @Override
            public void onClientRetryScheduled(
                    ServiceKey serviceKey,
                    int methodId,
                    int nextAttempt,
                    long delayMillis,
                    Throwable cause) {
                throw new IllegalStateException("observer failure");
            }
        };
        RpcObserver counting = new RpcObserver() {
            @Override
            public void onClientRetryScheduled(
                    ServiceKey serviceKey,
                    int methodId,
                    int nextAttempt,
                    long delayMillis,
                    Throwable cause) {
                calls.incrementAndGet();
            }
        };

        RpcObserver composite =
                RpcObserver.composite(List.of(failing, counting));
        assertTrue(composite.enabled());

        composite.onClientRetryScheduled(
                new ServiceKey("demo.Service", "1.0.0", "default"),
                1,
                2,
                10L,
                new RuntimeException("retry"));

        assertEquals(1, calls.get());
    }

    @Test
    void compositeShouldIgnoreDisabledObservers() {
        RpcObserver composite = RpcObserver.composite(
                List.of(RpcObserver.noop()));

        assertFalse(composite.enabled());
        composite.onClientAttemptCompleted(
                new ServiceKey("demo.Service", "1.0.0", "default"),
                1,
                null,
                1,
                1L,
                RpcStatus.OK,
                null);
    }
}
