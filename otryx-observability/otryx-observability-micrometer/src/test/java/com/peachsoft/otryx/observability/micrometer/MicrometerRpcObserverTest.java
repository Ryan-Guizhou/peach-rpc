package com.peachsoft.otryx.observability.micrometer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.peachsoft.otryx.api.RpcEndpoint;
import com.peachsoft.otryx.api.RpcExecutionMode;
import com.peachsoft.otryx.api.RpcStatus;
import com.peachsoft.otryx.api.ServiceKey;
import com.peachsoft.otryx.observability.RpcConnectionCloseReason;
import com.peachsoft.otryx.observability.RpcCircuitState;
import com.peachsoft.otryx.observability.RpcConnectionRole;
import com.peachsoft.otryx.observability.RpcRetryExhaustionReason;
import org.junit.jupiter.api.Test;

/**
 * Micrometer Observer 指标映射测试。
 *
 * @Author Ryan
 * @Version 1.0.0
 * @CreateTime 2026/9/29 14:51
 */
class MicrometerRpcObserverTest {

    @Test
    void shouldRecordConnectionAndClientMetrics() {
        SimpleMeterRegistry registry =
                new SimpleMeterRegistry();
        MicrometerRpcObserver observer =
                new MicrometerRpcObserver(registry);
        RpcEndpoint endpoint =
                new RpcEndpoint("127.0.0.1", 19090);

        observer.onConnectionEstablished(
                RpcConnectionRole.CLIENT,
                endpoint,
                1000L);
        observer.onClientAttemptCompleted(
                new ServiceKey(
                        "demo.Service",
                        "1.0.0",
                        "default"),
                1,
                endpoint,
                1,
                2000L,
                RpcStatus.OK,
                null);
        ServiceKey serviceKey =
                new ServiceKey(
                        "demo.Service",
                        "1.0.0",
                        "default");
        observer.onClientInflightChanged(1);
        observer.onClientCircuitStateChanged(
                serviceKey,
                1,
                RpcCircuitState.CLOSED);
        observer.onClientCallCompleted(
                serviceKey,
                1,
                2500L,
                RpcStatus.OK,
                null);
        observer.onClientInflightChanged(-1);
        observer.onClientCircuitStateChanged(
                serviceKey,
                1,
                RpcCircuitState.OPEN);
        observer.onClientCircuitStateChanged(
                serviceKey,
                1,
                RpcCircuitState.HALF_OPEN);
        observer.onClientCircuitStateChanged(
                serviceKey,
                1,
                RpcCircuitState.CLOSED);
        observer.onClientCallCompleted(
                serviceKey,
                1,
                3000L,
                RpcStatus.DEADLINE_EXCEEDED,
                null);
        observer.onClientRetryExhausted(
                serviceKey,
                1,
                RpcRetryExhaustionReason.MAX_ATTEMPTS,
                new IllegalStateException("failure"));
        observer.onClientCircuitRejected(
                new ServiceKey(
                        "demo.Service",
                        "1.0.0",
                        "default"),
                1);
        observer.onEndpointEjected(
                new ServiceKey(
                        "demo.Service",
                        "1.0.0",
                        "default"),
                endpoint,
                30000L);
        observer.onConnectionClosed(
                RpcConnectionRole.CLIENT,
                endpoint,
                RpcConnectionCloseReason.LOCAL_CLOSE,
                null);

        assertNotNull(registry.find(
                "otryx.rpc.client.attempts").timer());
        assertNotNull(registry.find(
                "otryx.rpc.client.calls").timer());
        assertEquals(
                1.0,
                registry.find(
                                "otryx.rpc.client.retry.exhausted")
                        .tag(
                                "reason",
                                RpcRetryExhaustionReason
                                        .MAX_ATTEMPTS
                                        .name())
                        .counter()
                        .count());
        assertEquals(
                1.0,
                registry.find(
                                "otryx.rpc.client.circuit.rejected")
                        .counter()
                        .count());
        assertEquals(
                1.0,
                registry.find(
                                "otryx.rpc.client.outlier.ejected")
                        .counter()
                        .count());
        assertEquals(
                1.0,
                registry.find(
                                "otryx.rpc.client.timeouts")
                        .counter()
                        .count());
        assertEquals(
                0.0,
                registry.find(
                                "otryx.rpc.client.inflight")
                        .gauge()
                        .value());
        assertEquals(
                1.0,
                registry.find(
                                "otryx.rpc.client.circuit.state")
                        .tag(
                                "state",
                                RpcCircuitState.CLOSED.name())
                        .gauge()
                        .value());
        assertEquals(
                0.0,
                registry.find(
                                "otryx.rpc.client.circuit.state")
                        .tag(
                                "state",
                                RpcCircuitState.OPEN.name())
                        .gauge()
                        .value());
        assertEquals(
                0.0,
                registry.find(
                                "otryx.rpc.client.circuit.state")
                        .tag(
                                "state",
                                RpcCircuitState.HALF_OPEN.name())
                        .gauge()
                        .value());
        assertNull(
                registry.find("otryx.rpc.client.attempts")
                        .timer()
                        .getId()
                        .getTag("service"));
        assertNull(
                registry.find("otryx.rpc.client.attempts")
                        .timer()
                        .getId()
                        .getTag("method"));
        assertEquals(
                1.0,
                registry.find(
                                "otryx.rpc.connection.closed")
                        .counter()
                        .count());
        assertEquals(
                0.0,
                registry.find(
                                "otryx.rpc.connection.active")
                        .gauge()
                        .value());
    }

    @Test
    void shouldRecordFailureAndOverloadCountersWithoutHighCardinalityTags() {
        SimpleMeterRegistry registry =
                new SimpleMeterRegistry();
        MicrometerRpcObserver observer =
                new MicrometerRpcObserver(registry);
        RpcEndpoint endpoint =
                new RpcEndpoint("127.0.0.1", 19090);
        ServiceKey serviceKey =
                new ServiceKey(
                        "demo.Service",
                        "1.0.0",
                        "default");

        observer.onClientAttemptCompleted(
                serviceKey,
                7,
                endpoint,
                1,
                1000L,
                RpcStatus.UNAVAILABLE,
                new IllegalStateException("failure"));
        observer.onServerInflightChanged(1);
        observer.onServerInvocationCompleted(
                11,
                7,
                RpcExecutionMode.CPU,
                1000L,
                RpcStatus.OVERLOADED,
                null);
        observer.onServerInflightChanged(-1);
        observer.onServerInflightBytesChanged(128L);
        assertEquals(
                128.0,
                registry.find("otryx.rpc.server.inflight.bytes")
                        .gauge()
                        .value());
        observer.onServerInflightBytesChanged(-128L);
        observer.onServerAdmissionRejected(
                11,
                7,
                "cpu-queue");

        assertEquals(
                1.0,
                registry.find("otryx.rpc.client.failures")
                        .counter()
                        .count());
        assertEquals(
                1.0,
                registry.find("otryx.rpc.server.failures")
                        .counter()
                        .count());
        assertEquals(
                1.0,
                registry.find("otryx.rpc.server.overloaded")
                        .counter()
                        .count());
        assertEquals(
                1.0,
                registry.find(
                                "otryx.rpc.server.admission.rejected")
                        .counter()
                        .count());
        assertEquals(
                0.0,
                registry.find(
                                "otryx.rpc.server.inflight")
                        .gauge()
                        .value());
        assertEquals(
                0.0,
                registry.find(
                                "otryx.rpc.server.inflight.bytes")
                        .gauge()
                        .value());
        assertNull(
                registry.find("otryx.rpc.server.invocations")
                        .timer()
                        .getId()
                        .getTag("service"));
        assertNull(
                registry.find("otryx.rpc.server.invocations")
                        .timer()
                        .getId()
                        .getTag("method"));
    }
}
