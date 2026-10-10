package com.peachsoft.otryx.core;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Consumer 容错原语热路径性能基准。
 *
 * <p>该基准刻意与 Core 使用相同 package，以便在不扩大生产 API 可见性的前提下，
 * 直接测量 package-private 的 Retry Budget、Circuit Breaker 与 Outlier 状态路径。
 */
@BenchmarkMode({Mode.SampleTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class ResiliencePathBenchmark {

    private RpcClientResilienceOptions options;
    private RetryBudget retryBudget;
    private RetryBudget saturatedRetryBudget;
    private RpcCircuitBreaker closedCircuit;
    private RpcCircuitBreaker openCircuit;
    private EndpointStats healthyEndpoint;
    private EndpointStats ejectedEndpoint;
    private EndpointStats successAccountingEndpoint;
    private EndpointStats failureAccountingEndpoint;

    /** 创建 Benchmark 状态。 */
    public ResiliencePathBenchmark() {
    }

    /** 每个测量迭代前重建相互独立的容错状态。 */
    @Setup(Level.Iteration)
    public void setup() {
        options = new RpcClientResilienceOptions(
                2,
                1.0d,
                1024,
                1_000_000,
                Duration.ZERO,
                Duration.ZERO,
                3,
                Duration.ofSeconds(30),
                3,
                Duration.ofSeconds(30));
        retryBudget = new RetryBudget(options);
        RpcClientResilienceOptions saturatedBudgetOptions =
                new RpcClientResilienceOptions(
                        2,
                        1.0d,
                        1024,
                        1024,
                        Duration.ZERO,
                        Duration.ZERO,
                        3,
                        Duration.ofSeconds(30),
                        3,
                        Duration.ofSeconds(30));
        saturatedRetryBudget =
                new RetryBudget(saturatedBudgetOptions);
        closedCircuit = new RpcCircuitBreaker(
                options.circuitConsecutiveFailureThreshold(),
                options.circuitOpenDuration());
        openCircuit = new RpcCircuitBreaker(
                1,
                Duration.ofSeconds(30));
        openCircuit.onFailure(openCircuit.tryAcquire());

        healthyEndpoint = new EndpointStats();
        healthyEndpoint.begin();
        healthyEndpoint.endSuccess(1_000L);

        ejectedEndpoint = new EndpointStats();
        for (int index = 0;
             index < options.outlierConsecutiveFailureThreshold();
             index++) {
            ejectedEndpoint.begin();
            ejectedEndpoint.endFailure(1_000L, options);
        }
        successAccountingEndpoint = new EndpointStats();
        failureAccountingEndpoint = new EndpointStats();
    }

    /**
     * 测量 Retry Budget 补充额度并申请一次重试的路径。
     *
     * @return 是否成功申请一个重试额度
     */
    @Benchmark
    public boolean retryBudgetAcquire() {
        retryBudget.onRequest();
        return retryBudget.tryAcquireRetry();
    }

    /**
     * 测量 Retry Budget 已达到上限时的健康请求记账路径。
     */
    @Benchmark
    public void retryBudgetSaturatedOnRequest() {
        saturatedRetryBudget.onRequest();
    }

    /**
     * 测量闭合 Circuit 的准入与成功完成路径。
     *
     * @return 调用是否获准进入
     */
    @Benchmark
    public boolean circuitClosedAcquireAndSuccess() {
        long generation = closedCircuit.tryAcquire();
        closedCircuit.onSuccess(generation);
        return generation != RpcCircuitBreaker.REJECTED;
    }

    /**
     * 测量 Circuit OPEN 状态下的快速拒绝路径。
     *
     * @return Circuit 保持 OPEN 时返回 false
     */
    @Benchmark
    public boolean circuitOpenReject() {
        return openCircuit.tryAcquire() != RpcCircuitBreaker.REJECTED;
    }

    /**
     * 测量健康 Endpoint 的可用性读取路径。
     *
     * @return 健康 Endpoint 返回 true
     */
    @Benchmark
    public boolean outlierHealthyRead() {
        return healthyEndpoint.available();
    }

    /**
     * 测量已剔除 Endpoint 的可用性读取路径。
     *
     * @return Endpoint 仍处于剔除窗口时返回 false
     */
    @Benchmark
    public boolean outlierEjectedRead() {
        return ejectedEndpoint.available();
    }

    /**
     * 测量健康 Endpoint 成功请求的记账路径。
     *
     * @return 本次完成后的 inflight 数量
     */
    @Benchmark
    public int outlierSuccessAccounting() {
        successAccountingEndpoint.begin();
        successAccountingEndpoint.endSuccess(1_000L);
        return successAccountingEndpoint.inflight();
    }

    /**
     * 测量 Outlier Ejection 前使用的原子失败记账路径。
     *
     * @return 本次完成后的 inflight 数量
     */
    @Benchmark
    public int outlierFailureAccounting() {
        failureAccountingEndpoint.begin();
        failureAccountingEndpoint.endFailure(1_000L, options);
        return failureAccountingEndpoint.inflight();
    }
}
