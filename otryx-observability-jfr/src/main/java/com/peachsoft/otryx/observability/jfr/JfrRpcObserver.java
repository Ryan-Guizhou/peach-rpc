package io.peach.rpc.observability.jfr;

import io.peach.rpc.api.RpcEndpoint;
import io.peach.rpc.api.RpcExecutionMode;
import io.peach.rpc.api.RpcStatus;
import io.peach.rpc.api.ServiceKey;
import io.peach.rpc.observability.RpcCertificateReloadOutcome;
import io.peach.rpc.observability.RpcConnectionRole;
import io.peach.rpc.observability.RpcObserver;
import io.peach.rpc.observability.RpcRegistryRecoveryAction;
import io.peach.rpc.observability.RpcSecurityMode;
import java.time.Duration;

/**
 * JFR Peach RPC Observer。
 *
 * <p>仅记录失败、恢复以及超过阈值的调用，避免高频正常请求持续产生事件。
 */
public final class JfrRpcObserver implements RpcObserver {

    private final long slowThresholdNanos;

    /**
     * 创建 JFR Observer。
     *
     * @param slowThreshold 慢调用阈值
     */
    public JfrRpcObserver(Duration slowThreshold) {
        if (slowThreshold == null
                || slowThreshold.isNegative()
                || slowThreshold.isZero()) {
            throw new IllegalArgumentException(
                    "slowThreshold must be positive");
        }
        this.slowThresholdNanos =
                slowThreshold.toNanos();
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
        if (error == null
                && status == RpcStatus.OK
                && durationNanos < slowThresholdNanos) {
            return;
        }
        RpcClientAttemptJfrEvent event =
                new RpcClientAttemptJfrEvent();
        if (!event.isEnabled()) {
            return;
        }
        event.service = serviceKey.canonicalName();
        event.methodId = methodId;
        event.status = status.name();
        event.durationNanos = durationNanos;
        event.commit();
    }

    @Override
    public void onClientRetryScheduled(
            ServiceKey serviceKey,
            int methodId,
            int nextAttempt,
            long delayMillis,
            Throwable cause) {
        RpcRetryJfrEvent event =
                new RpcRetryJfrEvent();
        if (!event.isEnabled()) {
            return;
        }
        event.nextAttempt = nextAttempt;
        event.delayMillis = delayMillis;
        event.causeType = cause == null
                ? "unknown"
                : cause.getClass().getName();
        event.commit();
    }

    @Override
    public void onConnectionReconnectScheduled(
            RpcEndpoint endpoint,
            int attempt,
            long delayMillis) {
        recovery(
                "connection",
                "reconnect",
                "SCHEDULED",
                Duration.ofMillis(delayMillis).toNanos());
    }

    @Override
    public void onConnectionHeartbeatTimeout(
            RpcConnectionRole role,
            RpcEndpoint endpoint,
            long idleNanos) {
        recovery(
                "connection",
                "heartbeat-timeout",
                role.name(),
                idleNanos);
    }

    @Override
    public void onRegistryRecoveryCompleted(
            String registryType,
            RpcRegistryRecoveryAction action,
            long durationNanos,
            Throwable error) {
        recovery(
                "registry:" + registryType,
                action.name(),
                error == null ? "SUCCESS" : "FAILURE",
                durationNanos);
    }

    @Override
    public void onTlsHandshakeCompleted(
            RpcConnectionRole role,
            RpcEndpoint endpoint,
            RpcSecurityMode mode,
            long durationNanos,
            Throwable error) {
        if (error == null
                && durationNanos < slowThresholdNanos) {
            return;
        }
        recovery(
                "tls:" + mode.name(),
                "handshake",
                error == null ? "SUCCESS" : "FAILURE",
                durationNanos);
    }

    @Override
    public void onCertificateReloadCompleted(
            RpcSecurityMode mode,
            RpcCertificateReloadOutcome outcome,
            long durationNanos,
            Throwable error) {
        recovery(
                "tls:" + mode.name(),
                "certificate-reload",
                outcome.name(),
                durationNanos);
    }

    @Override
    public void onServerInvocationCompleted(
            int serviceId,
            int methodId,
            RpcExecutionMode executionMode,
            long durationNanos,
            RpcStatus status,
            Throwable error) {
        if (error == null
                && status == RpcStatus.OK
                && durationNanos < slowThresholdNanos) {
            return;
        }
        RpcClientAttemptJfrEvent event =
                new RpcClientAttemptJfrEvent();
        if (!event.isEnabled()) {
            return;
        }
        event.service = Integer.toString(serviceId);
        event.methodId = methodId;
        event.status = status.name();
        event.durationNanos = durationNanos;
        event.commit();
    }

    private static void recovery(
            String domain,
            String action,
            String outcome,
            long durationNanos) {
        RpcRecoveryJfrEvent event =
                new RpcRecoveryJfrEvent();
        if (!event.isEnabled()) {
            return;
        }
        event.domain = domain;
        event.action = action;
        event.outcome = outcome;
        event.durationNanos = durationNanos;
        event.commit();
    }
}
