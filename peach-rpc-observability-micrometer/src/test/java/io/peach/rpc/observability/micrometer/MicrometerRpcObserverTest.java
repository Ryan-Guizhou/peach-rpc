package io.peach.rpc.observability.micrometer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.peach.rpc.api.RpcEndpoint;
import io.peach.rpc.api.RpcExecutionMode;
import io.peach.rpc.api.RpcStatus;
import io.peach.rpc.api.ServiceKey;
import io.peach.rpc.observability.RpcConnectionCloseReason;
import io.peach.rpc.observability.RpcCircuitState;
import io.peach.rpc.observability.RpcConnectionRole;
import org.junit.jupiter.api.Test;

/** Micrometer Observer 指标映射测试。 */
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
                "peach.rpc.client.attempts").timer());
        assertNotNull(registry.find(
                "peach.rpc.client.calls").timer());
        assertEquals(
                1.0,
                registry.find(
                                "peach.rpc.client.circuit.rejected")
                        .counter()
                        .count());
        assertEquals(
                1.0,
                registry.find(
                                "peach.rpc.client.outlier.ejected")
                        .counter()
                        .count());
        assertEquals(
                1.0,
                registry.find(
                                "peach.rpc.client.timeouts")
                        .counter()
                        .count());
        assertEquals(
                0.0,
                registry.find(
                                "peach.rpc.client.inflight")
                        .gauge()
                        .value());
        assertEquals(
                1.0,
                registry.find(
                                "peach.rpc.client.circuit.state")
                        .tag(
                                "state",
                                RpcCircuitState.CLOSED.name())
                        .gauge()
                        .value());
        assertEquals(
                0.0,
                registry.find(
                                "peach.rpc.client.circuit.state")
                        .tag(
                                "state",
                                RpcCircuitState.OPEN.name())
                        .gauge()
                        .value());
        assertEquals(
                0.0,
                registry.find(
                                "peach.rpc.client.circuit.state")
                        .tag(
                                "state",
                                RpcCircuitState.HALF_OPEN.name())
                        .gauge()
                        .value());
        assertNull(
                registry.find("peach.rpc.client.attempts")
                        .timer()
                        .getId()
                        .getTag("service"));
        assertNull(
                registry.find("peach.rpc.client.attempts")
                        .timer()
                        .getId()
                        .getTag("method"));
        assertEquals(
                1.0,
                registry.find(
                                "peach.rpc.connection.closed")
                        .counter()
                        .count());
        assertEquals(
                0.0,
                registry.find(
                                "peach.rpc.connection.active")
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
        observer.onServerAdmissionRejected(
                11,
                7,
                "cpu-queue");

        assertEquals(
                1.0,
                registry.find("peach.rpc.client.failures")
                        .counter()
                        .count());
        assertEquals(
                1.0,
                registry.find("peach.rpc.server.failures")
                        .counter()
                        .count());
        assertEquals(
                1.0,
                registry.find("peach.rpc.server.overloaded")
                        .counter()
                        .count());
        assertEquals(
                1.0,
                registry.find(
                                "peach.rpc.server.admission.rejected")
                        .counter()
                        .count());
        assertEquals(
                0.0,
                registry.find(
                                "peach.rpc.server.inflight")
                        .gauge()
                        .value());
        assertNull(
                registry.find("peach.rpc.server.invocations")
                        .timer()
                        .getId()
                        .getTag("service"));
        assertNull(
                registry.find("peach.rpc.server.invocations")
                        .timer()
                        .getId()
                        .getTag("method"));
    }
}
