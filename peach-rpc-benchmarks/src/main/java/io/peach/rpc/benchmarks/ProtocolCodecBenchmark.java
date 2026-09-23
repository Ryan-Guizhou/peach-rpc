package io.peach.rpc.benchmarks;

import io.peach.rpc.api.RpcStatus;
import io.peach.rpc.protocol.RpcFrame;
import io.peach.rpc.protocol.RpcMessageType;
import io.peach.rpc.protocol.RpcProtocolCodec;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/** 协议编解码微基准。 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 8, time = 1)
@State(Scope.Thread)
public class ProtocolCodecBenchmark {
    private RpcFrame frame;
    private byte[] encoded;

    @Setup(Level.Trial)
    public void setup() {
        frame = new RpcFrame(
                RpcMessageType.REQUEST,
                (byte) 1,
                RpcStatus.OK,
                1L,
                1001,
                2001,
                Map.of("deadlineEpochMillis", "1800000000000"),
                new byte[256]);
        encoded = RpcProtocolCodec.encode(frame);
    }

    @Benchmark
    public byte[] encode() {
        return RpcProtocolCodec.encode(frame);
    }

    @Benchmark
    public RpcFrame decode() {
        return RpcProtocolCodec.decode(encoded);
    }
}
