package io.peach.rpc.core;

import java.time.Duration;

/**
 * Consumer 重试、异常实例剔除与熔断配置。
 *
 * @param maxAttempts 单次逻辑调用最大尝试次数，包含首次调用
 * @param retryBudgetRatio 每个原始请求补充的重试额度比例
 * @param retryBudgetMinRetries 初始最低重试额度
 * @param retryBudgetMaxRetries 最大累计重试额度
 * @param retryBaseBackoff 首次重试最大退避窗口
 * @param retryMaxBackoff 最大退避窗口
 * @param outlierConsecutiveFailureThreshold 端点连续基础设施失败剔除阈值
 * @param outlierEjectionDuration 端点临时剔除时间
 * @param circuitConsecutiveFailureThreshold 方法连续基础设施失败熔断阈值
 * @param circuitOpenDuration 熔断打开时间
 */
public record RpcClientResilienceOptions(
        int maxAttempts,
        double retryBudgetRatio,
        int retryBudgetMinRetries,
        int retryBudgetMaxRetries,
        Duration retryBaseBackoff,
        Duration retryMaxBackoff,
        int outlierConsecutiveFailureThreshold,
        Duration outlierEjectionDuration,
        int circuitConsecutiveFailureThreshold,
        Duration circuitOpenDuration) {

    /** 默认生产保护参数；自动重试仅对 {@code @PeachRpcIdempotent} 方法生效。 */
    public static final RpcClientResilienceOptions DEFAULT =
            new RpcClientResilienceOptions(
                    2,
                    0.10d,
                    10,
                    100,
                    Duration.ofMillis(10),
                    Duration.ofMillis(100),
                    5,
                    Duration.ofSeconds(30),
                    20,
                    Duration.ofSeconds(10));

    /** 校验配置。 */
    public RpcClientResilienceOptions {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        if (Double.isNaN(retryBudgetRatio)
                || retryBudgetRatio < 0.0d
                || retryBudgetRatio > 1.0d) {
            throw new IllegalArgumentException(
                    "retryBudgetRatio must be between 0 and 1");
        }
        if (retryBudgetMinRetries < 0
                || retryBudgetMaxRetries < retryBudgetMinRetries) {
            throw new IllegalArgumentException("retry budget limits are invalid");
        }
        requireNonNegative(retryBaseBackoff, "retryBaseBackoff");
        requireNonNegative(retryMaxBackoff, "retryMaxBackoff");
        if (retryMaxBackoff.compareTo(retryBaseBackoff) < 0) {
            throw new IllegalArgumentException(
                    "retryMaxBackoff must be >= retryBaseBackoff");
        }
        if (outlierConsecutiveFailureThreshold <= 0
                || circuitConsecutiveFailureThreshold <= 0) {
            throw new IllegalArgumentException("failure thresholds must be positive");
        }
        requirePositive(outlierEjectionDuration, "outlierEjectionDuration");
        requirePositive(circuitOpenDuration, "circuitOpenDuration");
    }

    private static void requireNonNegative(Duration value, String name) {
        if (value == null || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
