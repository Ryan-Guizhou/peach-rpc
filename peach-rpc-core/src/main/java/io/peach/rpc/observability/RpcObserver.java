package io.peach.rpc.observability;

import io.peach.rpc.api.RpcEndpoint;
import io.peach.rpc.api.RpcExecutionMode;
import io.peach.rpc.api.RpcStatus;
import io.peach.rpc.api.ServiceKey;
import java.util.ArrayList;
import java.util.List;

/**
 * Peach RPC 低依赖可观测性事件契约。
 *
 * <p>Core 不依赖 Micrometer、OpenTelemetry 或 JFR Adapter。后续观测实现可消费本契约，
 * 将事件映射为指标、Trace 或 JFR Event。默认 NOOP Observer 的热路径不创建事件对象。
 *
 * <p>实现不应阻塞调用线程，也不应把异常传播回 RPC 主链。
 */
public interface RpcObserver {

    /**
     * 返回 Observer 是否启用。
     *
     * @return 是否需要产生观测回调
     */
    default boolean enabled() {
        return true;
    }

    /**
     * 连接握手完成并进入可用状态。
     *
     * @param role 本地连接角色
     * @param endpoint 对端端点
     * @param durationNanos 从 TCP connect/accept 到握手完成的耗时
     */
    default void onConnectionEstablished(
            RpcConnectionRole role,
            RpcEndpoint endpoint,
            long durationNanos) {
    }

    /**
     * Consumer 已安排下一次连接重建。
     *
     * @param endpoint 目标端点
     * @param attempt 连续连接失败后的重连序号，从 1 开始
     * @param delayMillis 计划 full-jitter 退避时间
     */
    default void onConnectionReconnectScheduled(
            RpcEndpoint endpoint,
            int attempt,
            long delayMillis) {
    }

    /**
     * Heartbeat 超时摘除连接。
     *
     * @param role 本地连接角色
     * @param endpoint 对端端点
     * @param idleNanos 自 PING 发出后等待入站活跃的时长
     */
    default void onConnectionHeartbeatTimeout(
            RpcConnectionRole role,
            RpcEndpoint endpoint,
            long idleNanos) {
    }

    /**
     * 连接关闭。
     *
     * @param role 本地连接角色
     * @param endpoint 对端端点
     * @param reason 归一化关闭原因
     * @param error 异常原因；正常关闭时为 null
     */
    default void onConnectionClosed(
            RpcConnectionRole role,
            RpcEndpoint endpoint,
            RpcConnectionCloseReason reason,
            Throwable error) {
    }

    /**
     * Registry 控制面操作完成。
     *
     * @param registryType Registry SPI 类型
     * @param operation 操作类型
     * @param durationNanos 操作耗时
     * @param error 失败原因；成功时为 null
     */
    default void onRegistryOperationCompleted(
            String registryType,
            RpcRegistryOperation operation,
            long durationNanos,
            Throwable error) {
    }

    /**
     * Registry 自动恢复动作完成。
     *
     * @param registryType Registry SPI 类型
     * @param action 恢复动作
     * @param durationNanos 恢复耗时
     * @param error 失败原因；成功时为 null
     */
    default void onRegistryRecoveryCompleted(
            String registryType,
            RpcRegistryRecoveryAction action,
            long durationNanos,
            Throwable error) {
    }

    /**
     * TLS/mTLS 握手完成。
     *
     * @param role 本地连接角色
     * @param endpoint 对端端点
     * @param mode Transport 安全模式
     * @param durationNanos TLS handshake 耗时
     * @param error 握手失败原因；成功时为 null
     */
    default void onTlsHandshakeCompleted(
            RpcConnectionRole role,
            RpcEndpoint endpoint,
            RpcSecurityMode mode,
            long durationNanos,
            Throwable error) {
    }

    /**
     * TLS 证书热更新完成。
     *
     * @param mode Transport 安全模式
     * @param outcome 更新结果
     * @param durationNanos reload 耗时
     * @param error 失败原因；成功时为 null
     */
    default void onCertificateReloadCompleted(
            RpcSecurityMode mode,
            RpcCertificateReloadOutcome outcome,
            long durationNanos,
            Throwable error) {
    }

    /**
     * 证书进入过期告警窗口。
     *
     * @param mode Transport 安全模式
     * @param remainingMillis 距证书过期的剩余毫秒数
     */
    default void onCertificateExpiryWarning(
            RpcSecurityMode mode,
            long remainingMillis) {
    }

    /**
     * Consumer 单次网络 attempt 完成。
     *
     * @param serviceKey 服务键
     * @param methodId 方法 ID
     * @param endpoint 目标端点；未选出实例时为 null
     * @param attempt attempt 序号，从 1 开始
     * @param durationNanos attempt 耗时
     * @param status 归一化 RPC 状态
     * @param error 失败原因；成功时为 null
     */
    default void onClientAttemptCompleted(
            ServiceKey serviceKey,
            int methodId,
            RpcEndpoint endpoint,
            int attempt,
            long durationNanos,
            RpcStatus status,
            Throwable error) {
    }

    /**
     * Consumer 已决定调度下一次 Retry。
     *
     * @param serviceKey 服务键
     * @param methodId 方法 ID
     * @param nextAttempt 下一次 attempt 序号
     * @param delayMillis 计划退避时间
     * @param cause 触发 Retry 的失败
     */
    default void onClientRetryScheduled(
            ServiceKey serviceKey,
            int methodId,
            int nextAttempt,
            long delayMillis,
            Throwable cause) {
    }

    /**
     * Provider 业务 invocation 完成。
     *
     * @param serviceId 服务 ID
     * @param methodId 方法 ID
     * @param executionMode 执行资源模式
     * @param durationNanos 业务 invocation 耗时
     * @param status 返回状态
     * @param error 框架执行失败；正常业务错误已编码到 status，通常为 null
     */
    default void onServerInvocationCompleted(
            int serviceId,
            int methodId,
            RpcExecutionMode executionMode,
            long durationNanos,
            RpcStatus status,
            Throwable error) {
    }

    /**
     * 返回全局 NOOP Observer。
     *
     * @return 不产生回调的 Observer
     */
    static RpcObserver noop() {
        return NoopHolder.INSTANCE;
    }

    /**
     * 合并多个 Observer。
     *
     * <p>单个 Observer 抛出的运行时异常会被隔离，避免观测系统影响 RPC 主链。
     *
     * @param observers Observer 集合
     * @return 组合后的 Observer
     */
    static RpcObserver composite(Iterable<? extends RpcObserver> observers) {
        List<RpcObserver> values = new ArrayList<>();
        for (RpcObserver observer : observers) {
            if (observer != null && observer.enabled()) {
                values.add(observer);
            }
        }
        if (values.isEmpty()) {
            return noop();
        }
        List<RpcObserver> immutable = List.copyOf(values);
        return new RpcObserver() {
            @Override
            public void onConnectionEstablished(
                    RpcConnectionRole role,
                    RpcEndpoint endpoint,
                    long durationNanos) {
                immutable.forEach(observer -> safely(() ->
                        observer.onConnectionEstablished(
                                role,
                                endpoint,
                                durationNanos)));
            }

            @Override
            public void onConnectionReconnectScheduled(
                    RpcEndpoint endpoint,
                    int attempt,
                    long delayMillis) {
                immutable.forEach(observer -> safely(() ->
                        observer.onConnectionReconnectScheduled(
                                endpoint,
                                attempt,
                                delayMillis)));
            }

            @Override
            public void onConnectionHeartbeatTimeout(
                    RpcConnectionRole role,
                    RpcEndpoint endpoint,
                    long idleNanos) {
                immutable.forEach(observer -> safely(() ->
                        observer.onConnectionHeartbeatTimeout(
                                role,
                                endpoint,
                                idleNanos)));
            }

            @Override
            public void onConnectionClosed(
                    RpcConnectionRole role,
                    RpcEndpoint endpoint,
                    RpcConnectionCloseReason reason,
                    Throwable error) {
                immutable.forEach(observer -> safely(() ->
                        observer.onConnectionClosed(
                                role,
                                endpoint,
                                reason,
                                error)));
            }

            @Override
            public void onRegistryOperationCompleted(
                    String registryType,
                    RpcRegistryOperation operation,
                    long durationNanos,
                    Throwable error) {
                immutable.forEach(observer -> safely(() ->
                        observer.onRegistryOperationCompleted(
                                registryType,
                                operation,
                                durationNanos,
                                error)));
            }

            @Override
            public void onRegistryRecoveryCompleted(
                    String registryType,
                    RpcRegistryRecoveryAction action,
                    long durationNanos,
                    Throwable error) {
                immutable.forEach(observer -> safely(() ->
                        observer.onRegistryRecoveryCompleted(
                                registryType,
                                action,
                                durationNanos,
                                error)));
            }

            @Override
            public void onTlsHandshakeCompleted(
                    RpcConnectionRole role,
                    RpcEndpoint endpoint,
                    RpcSecurityMode mode,
                    long durationNanos,
                    Throwable error) {
                immutable.forEach(observer -> safely(() ->
                        observer.onTlsHandshakeCompleted(
                                role,
                                endpoint,
                                mode,
                                durationNanos,
                                error)));
            }

            @Override
            public void onCertificateReloadCompleted(
                    RpcSecurityMode mode,
                    RpcCertificateReloadOutcome outcome,
                    long durationNanos,
                    Throwable error) {
                immutable.forEach(observer -> safely(() ->
                        observer.onCertificateReloadCompleted(
                                mode,
                                outcome,
                                durationNanos,
                                error)));
            }

            @Override
            public void onCertificateExpiryWarning(
                    RpcSecurityMode mode,
                    long remainingMillis) {
                immutable.forEach(observer -> safely(() ->
                        observer.onCertificateExpiryWarning(
                                mode,
                                remainingMillis)));
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
                immutable.forEach(observer -> safely(() ->
                        observer.onClientAttemptCompleted(
                                serviceKey,
                                methodId,
                                endpoint,
                                attempt,
                                durationNanos,
                                status,
                                error)));
            }

            @Override
            public void onClientRetryScheduled(
                    ServiceKey serviceKey,
                    int methodId,
                    int nextAttempt,
                    long delayMillis,
                    Throwable cause) {
                immutable.forEach(observer -> safely(() ->
                        observer.onClientRetryScheduled(
                                serviceKey,
                                methodId,
                                nextAttempt,
                                delayMillis,
                                cause)));
            }

            @Override
            public void onServerInvocationCompleted(
                    int serviceId,
                    int methodId,
                    RpcExecutionMode executionMode,
                    long durationNanos,
                    RpcStatus status,
                    Throwable error) {
                immutable.forEach(observer -> safely(() ->
                        observer.onServerInvocationCompleted(
                                serviceId,
                                methodId,
                                executionMode,
                                durationNanos,
                                status,
                                error)));
            }
        };
    }

    private static void safely(Runnable callback) {
        try {
            callback.run();
        } catch (RuntimeException ignored) {
            // Observability must never fail the RPC data path.
        }
    }

    /** NOOP Holder。 */
    final class NoopHolder {
        private static final RpcObserver INSTANCE = new RpcObserver() {
            @Override
            public boolean enabled() {
                return false;
            }
        };

        private NoopHolder() {
        }
    }
}
