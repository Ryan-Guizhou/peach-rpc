package io.peach.rpc.transport.vertx;

import io.peach.rpc.api.RpcStatus;
import io.peach.rpc.codec.RpcCodecIds;
import io.peach.rpc.protocol.RpcProtocolCodec;
import io.vertx.core.buffer.Buffer;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/** FrameAccumulator 完整帧与分片帧重组基准。 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class FrameAccumulatorBenchmark {

    /** Payload 字节数。 */
    @Param({"64", "256", "1024", "16384", "1048576"})
    public int payloadSize;

    /** 是否模拟 TCP 分片输入。 */
    @Param({"false", "true"})
    public boolean fragmented;

    private FrameAccumulator accumulator;
    private Buffer complete;
    private Buffer first;
    private Buffer second;
    private Consumer<byte[]> consumer;
    private int consumedLength;

    /** 创建帧累积基准。 */
    public FrameAccumulatorBenchmark() {
    }

    /** 准备固定帧与分片。 */
    @Setup
    public void setup() {
        byte[] frame = RpcProtocolCodec.encodeResponse(
                RpcCodecIds.FORY_NATIVE,
                RpcStatus.OK,
                42L,
                100,
                200,
                new byte[payloadSize]);
        accumulator =
                new FrameAccumulator(frame.length);
        complete = Buffer.buffer(frame);

        int split = Math.min(
                frame.length - 1,
                Math.max(
                        1,
                        RpcProtocolCodec.HEADER_LENGTH / 2));
        first = Buffer.buffer(
                java.util.Arrays.copyOfRange(
                        frame,
                        0,
                        split));
        second = Buffer.buffer(
                java.util.Arrays.copyOfRange(
                        frame,
                        split,
                        frame.length));
        consumer = this::consume;
    }

    /**
     * 重组一个完整或分片 RPC Frame。
     *
     * @return 消费的完整帧长度
     */
    @Benchmark
    public int accumulate() {
        consumedLength = 0;
        if (fragmented) {
            accumulator.accept(first, consumer);
            accumulator.accept(second, consumer);
        } else {
            accumulator.accept(
                    complete,
                    consumer);
        }
        return consumedLength;
    }

    private void consume(byte[] frame) {
        consumedLength = frame.length;
    }
}
