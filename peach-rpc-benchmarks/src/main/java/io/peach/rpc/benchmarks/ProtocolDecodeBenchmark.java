package io.peach.rpc.benchmarks;

import io.peach.rpc.api.RpcStatus;
import io.peach.rpc.protocol.RpcFrame;
import io.peach.rpc.protocol.RpcFrameView;
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

/**
 * 完整 Frame decode 与零 body-copy Frame View 的协议解析基准。
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class ProtocolDecodeBenchmark {

    private byte[] encoded;

    /** 创建协议解析基准。 */
    public ProtocolDecodeBenchmark() {
    }

    /** 准备 1 KiB Payload 的固定测试帧。 */
    @Setup
    public void setup() {
        encoded = RpcProtocolCodec.encode(new RpcFrame(
                RpcMessageType.REQUEST,
                (byte) 1,
                RpcStatus.OK,
                42L,
                100,
                200,
                Map.of(
                        "deadlineEpochMillis",
                        "2000000000000"),
                new byte[1024]));
    }

    /**
     * 测量兼容完整解码路径。
     *
     * @return 消费字段，防止 JIT 消除
     */
    @Benchmark
    public long fullDecode() {
        RpcFrame frame = RpcProtocolCodec.decode(encoded);
        return frame.requestId()
                + frame.payload().length
                + Long.parseLong(
                        frame.metadata().get(
                                "deadlineEpochMillis"));
    }

    /**
     * 测量零 body-copy view 路径。
     *
     * @return 消费字段，防止 JIT 消除
     */
    @Benchmark
    public long frameView() {
        RpcFrameView frame = RpcProtocolCodec.view(encoded);
        return frame.requestId()
                + frame.payloadLength()
                + frame.deadlineEpochMillis();
    }
}
