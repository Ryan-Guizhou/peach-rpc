package com.peachsoft.otryx.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * 验证端点运行统计、隔离到期及重新可用的判定。
 *
 * @Author Ryan
 * @Version 1.0.0
 * @CreateTime 2026/10/8 10:08
 */
class EndpointStatsTest {

    @Test
    void healthyEndpointShouldRemainAvailable() {
        EndpointStats stats = new EndpointStats();

        assertTrue(stats.available());
        assertTrue(stats.available());
    }

    @Test
    void expiredEjectionShouldBecomeAvailableAgain() throws Exception {
        RpcClientResilienceOptions options =
                new RpcClientResilienceOptions(
                        2,
                        0.1d,
                        1,
                        8,
                        Duration.ZERO,
                        Duration.ZERO,
                        1,
                        Duration.ofMillis(20),
                        3,
                        Duration.ofSeconds(1));
        EndpointStats stats = new EndpointStats();

        stats.begin();
        assertTrue(stats.endFailure(1_000L, options));
        assertFalse(stats.available());

        Thread.sleep(40L);

        assertTrue(stats.available());
        assertTrue(stats.available());
    }

    @Test
    void successShouldResetPreviousFailureSequence() {
        RpcClientResilienceOptions options =
                new RpcClientResilienceOptions(
                        2,
                        0.1d,
                        1,
                        8,
                        Duration.ZERO,
                        Duration.ZERO,
                        2,
                        Duration.ofSeconds(1),
                        3,
                        Duration.ofSeconds(1));
        EndpointStats stats = new EndpointStats();

        stats.begin();
        assertFalse(stats.endFailure(1_000L, options));

        stats.begin();
        stats.endSuccess(1_000L);

        stats.begin();
        assertFalse(stats.endFailure(1_000L, options));
        assertTrue(stats.available());
    }
}
