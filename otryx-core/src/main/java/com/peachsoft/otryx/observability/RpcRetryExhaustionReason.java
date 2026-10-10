package io.peach.rpc.observability;

/** Consumer 自动重试无法继续时的低基数原因。 */
public enum RpcRetryExhaustionReason {
    /** 已达到单次逻辑调用最大 Attempt 数。 */
    MAX_ATTEMPTS,
    /** 全局 Retry Budget 不允许继续放大流量。 */
    BUDGET,
    /** 剩余 Deadline 不足以容纳下一次退避和 Attempt。 */
    DEADLINE
}
