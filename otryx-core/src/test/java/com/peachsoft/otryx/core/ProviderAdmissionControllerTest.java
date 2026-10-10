package com.peachsoft.otryx.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Provider Admission 并发、内存预算以及竞态释放回归测试。 */
class ProviderAdmissionControllerTest {

    @Test
    void busyServiceMustNotConsumeAnotherServiceReservedSlots() {
        ProviderAdmissionController admission = controller(
                4,
                120,
                Map.of(10, Set.of(101), 20, Set.of(201)));

        ProviderAdmissionController.Lease first =
                acquire(admission, 10, 101, 20);
        ProviderAdmissionController.Lease second =
                acquire(admission, 10, 101, 20);
        assertEquals("service-concurrency",
                admission.tryAcquire(10, 101, 1).reason());

        ProviderAdmissionController.Lease otherFirst =
                acquire(admission, 20, 201, 20);
        ProviderAdmissionController.Lease otherSecond =
                acquire(admission, 20, 201, 20);
        assertEquals(4, admission.inFlightCalls());
        assertEquals(80L, admission.inFlightBytes());

        assertTrue(first.release());
        assertFalse(first.release());
        assertTrue(second.release());
        assertTrue(otherFirst.release());
        assertTrue(otherSecond.release());
        assertEquals(0, admission.inFlightCalls());
        assertEquals(0L, admission.inFlightBytes());
        assertTrue(acquire(admission, 10, 101, 20).release());
    }

    @Test
    void serviceInflightBytesMustRemainIsolated() {
        ProviderAdmissionController admission = controller(
                10,
                100,
                Map.of(10, Set.of(101), 20, Set.of(201)));
        ProviderAdmissionController.Lease first =
                acquire(admission, 10, 101, 32);
        assertEquals("service-inflight-bytes",
                admission.tryAcquire(10, 101, 32).reason());
        assertEquals("frame-exceeds-budget",
                admission.tryAcquire(10, 101, 51).reason());

        ProviderAdmissionController.Lease other =
                acquire(admission, 20, 201, 40);
        assertEquals(72L, admission.inFlightBytes());
        first.release();
        other.release();
        assertEquals(0L, admission.inFlightBytes());
        assertTrue(acquire(admission, 10, 101, 48).release());
    }

    @Test
    void methodConcurrencyCapMustIsolateSiblingMethod() {
        RpcProviderAdmissionOptions options =
                new RpcProviderAdmissionOptions(1024, 0, 1, 0, 0);
        ProviderAdmissionController admission =
                new ProviderAdmissionController(
                        4, options, Map.of(10, Set.of(101, 102)));

        ProviderAdmissionController.Lease first =
                acquire(admission, 10, 101, 16);
        assertEquals("method-concurrency",
                admission.tryAcquire(10, 101, 16).reason());
        ProviderAdmissionController.Lease sibling =
                acquire(admission, 10, 102, 16);
        first.release();
        sibling.release();
        assertEquals(0, admission.inFlightCalls());
    }

    @Test
    void methodByteCapMustRejectBeforeDecoding() {
        RpcProviderAdmissionOptions options =
                new RpcProviderAdmissionOptions(1024, 0, 0, 0, 40);
        ProviderAdmissionController admission =
                new ProviderAdmissionController(
                        4, options, Map.of(10, Set.of(101, 102)));
        ProviderAdmissionController.Lease first =
                acquire(admission, 10, 101, 24);
        assertEquals("method-inflight-bytes",
                admission.tryAcquire(10, 101, 24).reason());
        assertTrue(acquire(admission, 10, 102, 24).release());
        first.release();
        assertEquals(0L, admission.inFlightBytes());
    }

    @Test
    void rejectedAdmissionMustNeverLeavePartialReservations() {
        ProviderAdmissionController admission = controller(
                2, 100, Map.of(10, Set.of(101), 20, Set.of(201)));
        for (int index = 0; index < 1000; index++) {
            ProviderAdmissionController.Lease held =
                    acquire(admission, 10, 101, 40);
            assertEquals("service-concurrency",
                    admission.tryAcquire(10, 101, 8).reason());
            assertEquals(1, admission.inFlightCalls());
            assertEquals(40L, admission.inFlightBytes());
            held.release();
            assertEquals(0, admission.inFlightCalls());
            assertEquals(0L, admission.inFlightBytes());
        }
    }

    @Test
    void invalidPolicyOrInsufficientPerServiceBudgetMustFailFast() {
        assertThrows(IllegalArgumentException.class,
                () -> new RpcProviderAdmissionOptions(
                        0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new RpcProviderAdmissionOptions(
                        1024, -1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> controller(1, 100,
                        Map.of(10, Set.of(101), 20, Set.of(201))));
        assertThrows(IllegalArgumentException.class,
                () -> controller(10, 1,
                        Map.of(10, Set.of(101), 20, Set.of(201))));
    }

    @Test
    void simultaneousAcquisitionAndDoubleReleaseMustKeepCountersValid()
            throws Exception {
        ProviderAdmissionController admission = controller(
                32, 4096, Map.of(10, Set.of(101)));
        int workers = 8;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch begin = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(workers)) {
            List<Future<?>> tasks = new ArrayList<>();
            for (int worker = 0; worker < workers; worker++) {
                tasks.add(executor.submit(() -> {
                    ready.countDown();
                    try {
                        begin.await();
                        for (int index = 0; index < 1000; index++) {
                            ProviderAdmissionController.Decision decision =
                                    admission.tryAcquire(10, 101, 8);
                            if (decision.accepted()) {
                                decision.lease().release();
                                decision.lease().release();
                            }
                        }
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(interrupted);
                    }
                }));
            }
            assertTrue(ready.await(2, TimeUnit.SECONDS));
            begin.countDown();
            for (Future<?> task : tasks) {
                task.get(10, TimeUnit.SECONDS);
            }
        }
        assertEquals(0, admission.inFlightCalls());
        assertEquals(0L, admission.inFlightBytes());
    }

    private static ProviderAdmissionController controller(
            int maxConcurrent,
            long maxBytes,
            Map<Integer, Set<Integer>> serviceMethods) {
        return new ProviderAdmissionController(
                maxConcurrent,
                new RpcProviderAdmissionOptions(
                        maxBytes, 0, 0, 0, 0),
                serviceMethods);
    }

    private static ProviderAdmissionController.Lease acquire(
            ProviderAdmissionController controller,
            int serviceId,
            int methodId,
            int frameBytes) {
        ProviderAdmissionController.Decision decision =
                controller.tryAcquire(serviceId, methodId, frameBytes);
        assertNotNull(decision.lease(), () ->
                "Admission rejected: " + decision.reason());
        return decision.lease();
    }
}
