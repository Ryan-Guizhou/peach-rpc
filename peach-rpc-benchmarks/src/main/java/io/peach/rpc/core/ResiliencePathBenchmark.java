package io.peach.rpc.core;

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
 * Consumer resilience primitives hot-path benchmark.
 *
 * <p>This class intentionally shares the core package so it can measure the
 * package-private resilience primitives without widening the production API.
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
    private RpcCircuitBreaker closedCircuit;
    private RpcCircuitBreaker openCircuit;
    private EndpointStats healthyEndpoint;
    private EndpointStats ejectedEndpoint;
    private EndpointStats failureAccountingEndpoint;

    /** Create benchmark state. */
    public ResiliencePathBenchmark() {
    }

    /** Reset independent resilience states for each measurement iteration. */
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
        closedCircuit = new RpcCircuitBreaker(
                options.circuitConsecutiveFailureThreshold(),
                options.circuitOpenDuration());
        openCircuit = new RpcCircuitBreaker(
                1,
                Duration.ofSeconds(30));
        openCircuit.onFailure();

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
        failureAccountingEndpoint = new EndpointStats();
    }

    /**
     * Measure retry-budget refill and acquire path.
     *
     * @return whether one retry credit was acquired
     */
    @Benchmark
    public boolean retryBudgetAcquire() {
        retryBudget.onRequest();
        return retryBudget.tryAcquireRetry();
    }

    /**
     * Measure closed-circuit acquire plus successful completion.
     *
     * @return whether the call was admitted
     */
    @Benchmark
    public boolean circuitClosedAcquireAndSuccess() {
        boolean acquired = closedCircuit.tryAcquire();
        closedCircuit.onSuccess();
        return acquired;
    }

    /**
     * Measure fail-fast rejection while a circuit is open.
     *
     * @return false while the circuit remains open
     */
    @Benchmark
    public boolean circuitOpenReject() {
        return openCircuit.tryAcquire();
    }

    /**
     * Measure healthy endpoint availability read.
     *
     * @return true for a healthy endpoint
     */
    @Benchmark
    public boolean outlierHealthyRead() {
        return healthyEndpoint.available();
    }

    /**
     * Measure ejected endpoint availability read.
     *
     * @return false while the endpoint is ejected
     */
    @Benchmark
    public boolean outlierEjectedRead() {
        return ejectedEndpoint.available();
    }

    /**
     * Measure the atomic failure-accounting path used before outlier ejection.
     *
     * @return current inflight count after completion
     */
    @Benchmark
    public int outlierFailureAccounting() {
        failureAccountingEndpoint.begin();
        failureAccountingEndpoint.endFailure(1_000L, options);
        return failureAccountingEndpoint.inflight();
    }
}
