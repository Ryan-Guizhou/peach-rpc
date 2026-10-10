package io.peach.rpc.observability;

/** Consumer 方法级 Circuit Breaker 的稳定观测状态。 */
public enum RpcCircuitState {
    /** 正常放行调用。 */
    CLOSED,
    /** 拒绝调用，等待 open duration。 */
    OPEN,
    /** Open 到期后正在执行唯一探测调用。 */
    HALF_OPEN
}
