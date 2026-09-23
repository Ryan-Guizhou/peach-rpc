package io.peach.rpc.benchmarks;

import io.peach.rpc.api.RpcStatus;
import io.peach.rpc.codec.RpcCodecIds;
import io.peach.rpc.protocol.RpcFrame;
import io.peach.rpc.protocol.RpcMessageType;
import io.peach.rpc.protocol.RpcProtocolCodec;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/** 通用协议编码与 Unary 快路径编码基准。 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class ProtocolEncodeBenchmark {

    private static final long DEADLINE = 2_000_000_000_123L;
    private byte[] payload;

    /** 创建协议编码基准。 */
    public ProtocolEncodeBenchmark() {
    }

    /** 准备固定 256B Payload。 */
    @Setup
    public void setup() {
        payload = new byte[256];
    }

    /**
     * 测量通用 RpcFrame + Metadata Map 编码。
     *
     * @return 完整帧
     */
    @Benchmark
    public byte[] genericRequestEncode() {
        return RpcProtocolCodec.encode(new RpcFrame(
                RpcMessageType.REQUEST,
                RpcCodecIds.FORY_NATIVE,
                RpcStatus.OK,
                0L,
                100,
                200,
                Map.of(
                        "deadlineEpochMillis",
                        Long.toString(DEADLINE)),
                payload));
    }

    /**
     * 测量 Unary REQUEST 专用编码。
     *
     * @return 完整帧
     */
    @Benchmark
    public byte[] unaryRequestFastEncode() {
        return RpcProtocolCodec.encodeRequest(
                RpcCodecIds.FORY_NATIVE,
                100,
                200,
                DEADLINE,
                payload);
    }
}
