package com.peachsoft.otryx.observability;

/**
 * Consumer 自动重试无法继续时的低基数原因。
 *
 * @Author Ryan
 * @Version 1.0.0-SNAPSHOT
 * @CreateTime 2026/9/30 14:09
 */
public enum RpcRetryExhaustionReason {
    /** 已达到单次逻辑调用最大 Attempt 数。 */
    MAX_ATTEMPTS,
    /** 全局 Retry Budget 不允许继续放大流量。 */
    BUDGET,
    /** 剩余 Deadline 不足以容纳下一次退避和 Attempt。 */
    DEADLINE
}
