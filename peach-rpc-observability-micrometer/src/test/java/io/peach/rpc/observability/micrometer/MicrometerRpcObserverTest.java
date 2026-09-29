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
        observer.onConnectionClosed(
                RpcConnectionRole.CLIENT,
                endpoint,
                RpcConnectionCloseReason.LOCAL_CLOSE,
                null);

        assertNotNull(registry.find(
                "peach.rpc.client.attempts").timer());
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
        observer.onServerInvocationCompleted(
                11,
                7,
                RpcExecutionMode.CPU,
                1000L,
                RpcStatus.OVERLOADED,
                null);

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
