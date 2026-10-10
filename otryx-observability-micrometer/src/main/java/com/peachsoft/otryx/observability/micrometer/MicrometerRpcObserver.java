package io.peach.rpc.observability.micrometer;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.peach.rpc.api.RpcEndpoint;
import io.peach.rpc.api.RpcExecutionMode;
import io.peach.rpc.api.RpcStatus;
import io.peach.rpc.api.ServiceKey;
import io.peach.rpc.observability.RpcCertificateReloadOutcome;
import io.peach.rpc.observability.RpcCircuitState;
import io.peach.rpc.observability.RpcConnectionCloseReason;
import io.peach.rpc.observability.RpcConnectionRole;
import io.peach.rpc.observability.RpcFailureClassifier;
import io.peach.rpc.observability.RpcObserver;
import io.peach.rpc.observability.RpcRegistryOperation;
import io.peach.rpc.observability.RpcRegistryRecoveryAction;
import io.peach.rpc.observability.RpcRetryExhaustionReason;
import io.peach.rpc.observability.RpcSecurityMode;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 将 Peach RPC 低依赖 Observer 事件映射为 Micrometer 指标。
 *
 * <p>默认标签不包含 Endpoint、InstanceId、异常消息或 TraceId，避免高基数。
 */
public final class MicrometerRpcObserver implements RpcObserver {

    private final MeterRegistry registry;
    private final AtomicInteger activeConnections =
            new AtomicInteger();
    private final AtomicInteger clientInflight =
            new AtomicInteger();
    private final AtomicInteger serverInflight =
            new AtomicInteger();
    private final AtomicLong serverInflightBytes =
            new AtomicLong();
    private final AtomicInteger circuitClosed =
            new AtomicInteger();
    private final AtomicInteger circuitOpen =
            new AtomicInteger();
    private final AtomicInteger circuitHalfOpen =
            new AtomicInteger();
    private final ConcurrentMap<String, RpcCircuitState> circuitStates =
            new ConcurrentHashMap<>();

    /**
     * 创建 Micrometer Observer。
     *
     * @param registry MeterRegistry
     */
    public MicrometerRpcObserver(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(
                registry,
                "registry");
        Gauge.builder(
                        "peach.rpc.connection.active",
                        activeConnections,
                        AtomicInteger::get)
                .description("Active Peach RPC transport connections")
                .register(registry);
        Gauge.builder(
                        "peach.rpc.client.inflight",
                        clientInflight,
                        AtomicInteger::get)
                .description("Inflight logical Peach RPC client calls")
                .register(registry);
        Gauge.builder(
                        "peach.rpc.server.inflight",
                        serverInflight,
                        AtomicInteger::get)
                .description("Inflight Peach RPC provider invocations")
                .register(registry);
        Gauge.builder(
                        "peach.rpc.server.inflight.bytes",
                        serverInflightBytes,
                        AtomicLong::get)
                .baseUnit("bytes")
                .description("Accepted Peach RPC provider request Frame bytes")
                .register(registry);
        registerCircuitGauge(
                RpcCircuitState.CLOSED,
                circuitClosed);
        registerCircuitGauge(
                RpcCircuitState.OPEN,
                circuitOpen);
        registerCircuitGauge(
                RpcCircuitState.HALF_OPEN,
                circuitHalfOpen);
    }

    @Override
    public void onConnectionEstablished(
            RpcConnectionRole role,
            RpcEndpoint endpoint,
            long durationNanos) {
        activeConnections.incrementAndGet();
        timer(
                "peach.rpc.connection.established",
                "role",
                role.name())
                .record(
                        Duration.ofNanos(
                                Math.max(0L, durationNanos)));
    }

    @Override
    public void onConnectionReconnectScheduled(
            RpcEndpoint endpoint,
            int attempt,
            long delayMillis) {
        registry.counter(
                "peach.rpc.connection.reconnects")
                .increment();
    }

    @Override
    public void onConnectionHeartbeatTimeout(
            RpcConnectionRole role,
            RpcEndpoint endpoint,
            long idleNanos) {
        registry.counter(
                        "peach.rpc.connection.heartbeat.timeouts",
                        "role",
                        role.name())
                .increment();
    }

    @Override
    public void onConnectionClosed(
            RpcConnectionRole role,
            RpcEndpoint endpoint,
            RpcConnectionCloseReason reason,
            Throwable error) {
        activeConnections.updateAndGet(
                current -> Math.max(0, current - 1));
        registry.counter(
                        "peach.rpc.connection.closed",
                        "role",
                        role.name(),
                        "reason",
                        reason.name())
                .increment();
    }

    @Override
    public void onRegistryOperationCompleted(
            String registryType,
            RpcRegistryOperation operation,
            long durationNanos,
            Throwable error) {
        timer(
                "peach.rpc.registry.operations",
                "registry",
                safe(registryType),
                "operation",
                operation.name(),
                "outcome",
                outcome(error))
                .record(Duration.ofNanos(
                        Math.max(0L, durationNanos)));
        if (error != null) {
            registry.counter(
                            "peach.rpc.registry.failures",
                            "registry",
                            safe(registryType),
                            "operation",
                            operation.name())
                    .increment();
        }
    }

    @Override
    public void onRegistryRecoveryCompleted(
            String registryType,
            RpcRegistryRecoveryAction action,
            long durationNanos,
            Throwable error) {
        timer(
                "peach.rpc.registry.recoveries",
                "registry",
                safe(registryType),
                "action",
                action.name(),
                "outcome",
                outcome(error))
                .record(Duration.ofNanos(
                        Math.max(0L, durationNanos)));
    }

    @Override
    public void onTlsHandshakeCompleted(
            RpcConnectionRole role,
            RpcEndpoint endpoint,
            RpcSecurityMode mode,
            long durationNanos,
            Throwable error) {
        timer(
                "peach.rpc.tls.handshake",
                "role",
                role.name(),
                "mode",
                mode.name(),
                "outcome",
                outcome(error))
                .record(Duration.ofNanos(
                        Math.max(0L, durationNanos)));
        if (error != null) {
            registry.counter(
                            "peach.rpc.tls.handshake.failures",
                            "role",
                            role.name(),
                            "mode",
                            mode.name())
                    .increment();
        }
    }

    @Override
    public void onCertificateReloadCompleted(
            RpcSecurityMode mode,
            RpcCertificateReloadOutcome outcome,
            long durationNanos,
            Throwable error) {
        registry.counter(
                        "peach.rpc.tls.certificate.reload",
                        "mode",
                        mode.name(),
                        "outcome",
                        outcome.name())
                .increment();
    }

    @Override
    public void onCertificateExpiryWarning(
            RpcSecurityMode mode,
            long remainingMillis) {
        registry.counter(
                        "peach.rpc.tls.certificate.expiry.warnings",
                        "mode",
                        mode.name())
                .increment();
    }

    @Override
    public void onClientAttemptCompleted(
            ServiceKey serviceKey,
            int methodId,
            RpcEndpoint endpoint,
            int attempt,
            long durationNanos,
            RpcStatus status,
            Throwable error) {
        timer(
                "peach.rpc.client.attempts",
                "status",
                status.name(),
                "category",
                RpcFailureClassifier.classify(
                                status,
                                error)
                        .name())
                .record(Duration.ofNanos(
                        Math.max(0L, durationNanos)));
        if (error != null || status != RpcStatus.OK) {
            registry.counter(
                            "peach.rpc.client.failures",
                            "status",
                            status.name())
                    .increment();
        }
    }

    @Override
    public void onClientInflightChanged(int delta) {
        clientInflight.updateAndGet(
                current -> Math.max(0, current + delta));
    }

    @Override
    public void onServerInflightChanged(int delta) {
        serverInflight.updateAndGet(
                current -> Math.max(0, current + delta));
    }

    @Override
    public void onServerInflightBytesChanged(long delta) {
        serverInflightBytes.updateAndGet(current ->
                Math.max(0L, current + delta));
    }

    @Override
    public void onClientCircuitStateChanged(
            ServiceKey serviceKey,
            int methodId,
            RpcCircuitState state) {
        String key = serviceKey.canonicalName()
                + '#'
                + methodId;
        circuitStates.compute(key, (ignored, previous) -> {
            if (previous == state) {
                return state;
            }
            if (previous != null) {
                circuitCounter(previous)
                        .updateAndGet(value ->
                                Math.max(0, value - 1));
            }
            circuitCounter(state).incrementAndGet();
            return state;
        });
    }

    @Override
    public void onClientCallCompleted(
            ServiceKey serviceKey,
            int methodId,
            long durationNanos,
            RpcStatus status,
            Throwable error) {
        timer(
                "peach.rpc.client.calls",
                "status",
                status.name(),
                "category",
                RpcFailureClassifier.classify(
                                status,
                                error)
                        .name())
                .record(Duration.ofNanos(
                        Math.max(0L, durationNanos)));
        if (status == RpcStatus.DEADLINE_EXCEEDED) {
            registry.counter(
                    "peach.rpc.client.timeouts")
                    .increment();
        }
    }

    @Override
    public void onClientRetryExhausted(
            ServiceKey serviceKey,
            int methodId,
            RpcRetryExhaustionReason reason,
            Throwable cause) {
        registry.counter(
                        "peach.rpc.client.retry.exhausted",
                        "reason",
                        reason.name())
                .increment();
    }

    @Override
    public void onClientCircuitRejected(
            ServiceKey serviceKey,
            int methodId) {
        registry.counter(
                "peach.rpc.client.circuit.rejected")
                .increment();
    }

    @Override
    public void onEndpointEjected(
            ServiceKey serviceKey,
            RpcEndpoint endpoint,
            long ejectionMillis) {
        registry.counter(
                "peach.rpc.client.outlier.ejected")
                .increment();
    }

    @Override
    public void onClientRetryScheduled(
            ServiceKey serviceKey,
            int methodId,
            int nextAttempt,
            long delayMillis,
            Throwable cause) {
        registry.counter(
                        "peach.rpc.client.retries")
                .increment();
    }

    @Override
    public void onServerAdmissionRejected(
            int serviceId,
            int methodId,
            String reason) {
        registry.counter(
                        "peach.rpc.server.admission.rejected",
                        "reason",
                        safe(reason))
                .increment();
    }

    @Override
    public void onServerInvocationCompleted(
            int serviceId,
            int methodId,
            RpcExecutionMode executionMode,
            long durationNanos,
            RpcStatus status,
            Throwable error) {
        timer(
                "peach.rpc.server.invocations",
                "execution",
                executionMode.name(),
                "status",
                status.name(),
                "category",
                RpcFailureClassifier.classify(
                                status,
                                error)
                        .name())
                .record(Duration.ofNanos(
                        Math.max(0L, durationNanos)));
        if (error != null || status != RpcStatus.OK) {
            registry.counter(
                            "peach.rpc.server.failures",
                            "status",
                            status.name())
                    .increment();
        }
        if (status == RpcStatus.OVERLOADED) {
            registry.counter(
                            "peach.rpc.server.overloaded",
                            "execution",
                            executionMode.name())
                    .increment();
        }
    }

    private void registerCircuitGauge(
            RpcCircuitState state,
            AtomicInteger value) {
        Gauge.builder(
                        "peach.rpc.client.circuit.state",
                        value,
                        AtomicInteger::get)
                .tag("state", state.name())
                .description(
                        "Peach RPC client method circuit breakers by state")
                .register(registry);
    }

    private AtomicInteger circuitCounter(
            RpcCircuitState state) {
        return switch (state) {
            case CLOSED -> circuitClosed;
            case OPEN -> circuitOpen;
            case HALF_OPEN -> circuitHalfOpen;
        };
    }

    private Timer timer(
            String name,
            String... tags) {
        return registry.timer(name, tags);
    }

    private static String outcome(Throwable error) {
        return error == null ? "SUCCESS" : "FAILURE";
    }

    private static String safe(String value) {
        return value == null || value.isBlank()
                ? "unknown"
                : value;
    }
}
