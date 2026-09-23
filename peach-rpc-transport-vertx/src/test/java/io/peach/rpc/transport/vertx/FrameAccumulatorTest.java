package io.peach.rpc.transport.vertx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.peach.rpc.api.RpcStatus;
import io.peach.rpc.protocol.RpcFrame;
import io.peach.rpc.protocol.RpcMessageType;
import io.peach.rpc.protocol.RpcProtocolCodec;
import io.vertx.core.buffer.Buffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FrameAccumulatorTest {

    @Test
    void shouldAcceptMultipleFramesWhoseCombinedReadExceedsSingleFrameLimit() {
        byte[] first = frame(1L, new byte[] {1});
        byte[] second = frame(2L, new byte[] {2});
        int maxFrameBytes = Math.max(first.length, second.length);
        FrameAccumulator accumulator = new FrameAccumulator(maxFrameBytes);
        List<byte[]> decoded = new ArrayList<>();

        accumulator.accept(Buffer.buffer().appendBytes(first).appendBytes(second), decoded::add);

        assertEquals(2, decoded.size());
        assertArrayEquals(first, decoded.get(0));
        assertArrayEquals(second, decoded.get(1));
    }

    @Test
    void shouldRemainWritableAfterExactlyConsumedFrame() {
        byte[] first = frame(11L, new byte[] {1, 2});
        byte[] second = frame(12L, new byte[] {3, 4});
        int maxFrameBytes = Math.max(first.length, second.length);
        FrameAccumulator accumulator =
                new FrameAccumulator(maxFrameBytes);
        List<byte[]> decoded = new ArrayList<>();

        accumulator.accept(Buffer.buffer(first), decoded::add);
        accumulator.accept(Buffer.buffer(second), decoded::add);

        assertEquals(2, decoded.size());
        assertArrayEquals(first, decoded.get(0));
        assertArrayEquals(second, decoded.get(1));
    }

    private static byte[] frame(long requestId, byte[] payload) {
        return RpcProtocolCodec.encode(new RpcFrame(
                RpcMessageType.RESPONSE,
                (byte) 1,
                RpcStatus.OK,
                requestId,
                10,
                20,
                Map.of(),
                payload));
    }
}
