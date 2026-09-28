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
