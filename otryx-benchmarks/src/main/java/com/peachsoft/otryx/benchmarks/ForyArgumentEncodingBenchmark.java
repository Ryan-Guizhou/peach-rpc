package com.peachsoft.otryx.benchmarks;

import com.peachsoft.otryx.api.RpcMethodDescriptor;
import com.peachsoft.otryx.api.ServiceKey;
import com.peachsoft.otryx.codec.RpcMethodCodec;
import com.peachsoft.otryx.codec.fory.ForyRpcCodec;
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
 * Fory MethodCodec 参数序列化及零参数数组分配的可重复微基准。
 *
 * <p>与 Wire v1 对齐的 Object[] 载荷不作格式修改。该基准仅观察 Codec
 * 层，不代表完整 RPC 的网络吞吐、p99 或 10k 并发。
 */
@BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
@State(Scope.Thread)
public class ForyArgumentEncodingBenchmark {

    private static final String ARGUMENT = "otryx";
    private RpcMethodCodec zeroArguments;
    private RpcMethodCodec oneArgument;
    private RpcMethodCodec fourArguments;

    /** JMH 通过公共无参构造器创建线程隔离状态。 */
    public ForyArgumentEncodingBenchmark() {
    }

    /** 在计时区间外完成服务方法与 Fory Codec 绑定。 */
    @Setup(Level.Trial)
    public void setup() throws NoSuchMethodException {
        ForyRpcCodec codec = new ForyRpcCodec();
        ServiceKey service = new ServiceKey(
                ExampleService.class.getName(),
                "1.0.0",
                "benchmark");
        zeroArguments = codec.bind(RpcMethodDescriptor.from(
                service,
                ExampleService.class.getMethod("zero")));
        oneArgument = codec.bind(RpcMethodDescriptor.from(
                service,
                ExampleService.class.getMethod(
                        "one", String.class)));
        fourArguments = codec.bind(RpcMethodDescriptor.from(
                service,
                ExampleService.class.getMethod(
                        "four",
                        String.class,
                        String.class,
                        String.class,
                        String.class)));
    }

    /**
     * 测量避免零参数临时 Object[] 构造的内部快路径。
     *
     * @return Wire v1 Fory 参数载荷
     */
    @Benchmark
    public byte[] zeroArgumentFastPath() {
        return zeroArguments.encode0();
    }

    /**
     * 模拟改造前每次构造 Object[0] 并走统一参数编码的调用路径。
     *
     * @return Wire v1 Fory 参数载荷
     */
    @Benchmark
    public byte[] zeroArgumentLegacyStyle() {
        return zeroArguments.encodeArguments(new Object[0]);
    }

    /**
     * 单参数调用仍使用兼容 Object[] 载荷。
     *
     * @return Wire v1 Fory 参数载荷
     */
    @Benchmark
    public byte[] oneArgumentFastPath() {
        return oneArgument.encode1(ARGUMENT);
    }

    /**
     * 四参数调用仍使用兼容 Object[] 载荷。
     *
     * @return Wire v1 Fory 参数载荷
     */
    @Benchmark
    public byte[] fourArgumentFastPath() {
        return fourArguments.encode4(
                ARGUMENT, ARGUMENT, ARGUMENT, ARGUMENT);
    }

    /** 供 Benchmark 构造方法签名的无实现接口。 */
    public interface ExampleService {
        /** 零参数方法。 */
        String zero();

        /**
         * 单参数方法。
         *
         * @param value 参数
         * @return 值
         */
        String one(String value);

        /**
         * 四参数方法。
         *
         * @param a 参数 A
         * @param b 参数 B
         * @param c 参数 C
         * @param d 参数 D
         * @return 值
         */
        String four(String a, String b, String c, String d);
    }
}
