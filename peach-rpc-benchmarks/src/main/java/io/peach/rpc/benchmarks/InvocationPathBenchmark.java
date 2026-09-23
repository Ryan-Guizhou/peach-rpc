package io.peach.rpc.benchmarks;

import io.peach.rpc.generated.RpcGeneratedClients;
import io.peach.rpc.generated.RpcGeneratedInvocation;
import io.peach.rpc.proxy.bytebuddy.ByteBuddyProxyFactory;
import io.peach.rpc.proxy.jdk.JdkProxyFactory;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * Generated Stub、JDK Proxy 与 Byte Buddy fallback 的纯调用入口基准。
 *
 * <p>该基准不包含网络和序列化，用于单独观察代理/分派层附加成本。
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class InvocationPathBenchmark {

    private static final String VALUE = "peach-rpc";
    private static final CompletableFuture<Object> RESULT =
            CompletableFuture.completedFuture(VALUE);

    private BenchmarkService generated;
    private BenchmarkService jdk;
    private BenchmarkService byteBuddy;

    /** 创建调用路径基准。 */
    public InvocationPathBenchmark() {
    }

    /** 初始化三种调用路径。 */
    @Setup
    public void setup() {
        generated = RpcGeneratedClients.find(BenchmarkService.class)
                .orElseThrow()
                .create(new BenchmarkGeneratedInvocation());
        jdk = new JdkProxyFactory().create(
                BenchmarkService.class,
                (method, arguments) -> RESULT);
        byteBuddy = new ByteBuddyProxyFactory().create(
                BenchmarkService.class,
                (method, arguments) -> RESULT);
    }

    /**
     * 测量 Generated Stub 调用开销。
     *
     * @return 返回值
     */
    @Benchmark
    public String generatedStub() {
        return generated.echo(VALUE);
    }

    /**
     * 测量 JDK Proxy fallback 调用开销。
     *
     * @return 返回值
     */
    @Benchmark
    public String jdkProxy() {
        return jdk.echo(VALUE);
    }

    /**
     * 测量 Byte Buddy fallback 调用开销。
     *
     * @return 返回值
     */
    @Benchmark
    public String byteBuddyProxy() {
        return byteBuddy.echo(VALUE);
    }

    private static final class BenchmarkGeneratedInvocation
            implements RpcGeneratedInvocation {

        @Override
        public CompletionStage<Object> invoke0(int methodId) {
            return RESULT;
        }

        @Override
        public CompletionStage<Object> invoke1(
                int methodId,
                Object argument0) {
            return RESULT;
        }

        @Override
        public CompletionStage<Object> invoke2(
                int methodId,
                Object argument0,
                Object argument1) {
            return RESULT;
        }

        @Override
        public CompletionStage<Object> invoke3(
                int methodId,
                Object argument0,
                Object argument1,
                Object argument2) {
            return RESULT;
        }

        @Override
        public CompletionStage<Object> invoke4(
                int methodId,
                Object argument0,
                Object argument1,
                Object argument2,
                Object argument3) {
            return RESULT;
        }

        @Override
        public CompletionStage<Object> invokeN(
                int methodId,
                Object[] arguments) {
            return RESULT;
        }
    }
}
