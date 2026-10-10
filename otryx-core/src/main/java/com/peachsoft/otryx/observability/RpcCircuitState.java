package com.peachsoft.otryx.observability;

/**
 * Consumer 方法级 Circuit Breaker 的稳定观测状态。
 *
 * @Author Ryan
 * @Version 1.0.0-SNAPSHOT
 * @CreateTime 2026/9/30 11:45
 */
public enum RpcCircuitState {
    /** 正常放行调用。 */
    CLOSED,
    /** 拒绝调用，等待 open duration。 */
    OPEN,
    /** Open 到期后正在执行唯一探测调用。 */
    HALF_OPEN
}
