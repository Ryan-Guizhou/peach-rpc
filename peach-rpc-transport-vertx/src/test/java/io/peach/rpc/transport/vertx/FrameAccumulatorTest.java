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
import java.util.Random;
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
    void shouldReassembleFragmentedHeaderAndPayload() {
        byte[] frame = frame(
                21L,
                new byte[1024]);
        FrameAccumulator accumulator =
                new FrameAccumulator(frame.length);
        List<byte[]> decoded =
                new ArrayList<>();

        int split =
                RpcProtocolCodec.HEADER_LENGTH / 2;
        accumulator.accept(
                Buffer.buffer(
                        java.util.Arrays.copyOfRange(
                                frame,
                                0,
                                split)),
                decoded::add);
        assertEquals(0, decoded.size());

        accumulator.accept(
                Buffer.buffer(
                        java.util.Arrays.copyOfRange(
                                frame,
                                split,
                                frame.length)),
                decoded::add);

        assertEquals(1, decoded.size());
        assertArrayEquals(
                frame,
                decoded.get(0));
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

    @Test
    void shouldReassembleDeterministicRandomFragmentation() {
        Random random = new Random(20260930L);
        for (int iteration = 0;
                iteration < 100;
                iteration++) {
            byte[] expected = frame(
                    iteration + 1L,
                    new byte[1 + random.nextInt(4096)]);
            FrameAccumulator accumulator =
                    new FrameAccumulator(expected.length);
            List<byte[]> decoded = new ArrayList<>();

            int offset = 0;
            while (offset < expected.length) {
                int remaining = expected.length - offset;
                int length = Math.min(
                        remaining,
                        1 + random.nextInt(
                                Math.min(remaining, 97)));
                accumulator.accept(
                        Buffer.buffer(
                                java.util.Arrays.copyOfRange(
                                        expected,
                                        offset,
                                        offset + length)),
                        decoded::add);
                offset += length;
            }

            assertEquals(1, decoded.size());
            assertArrayEquals(
                    expected,
                    decoded.get(0));
        }
    }

    @Test
    void shouldReassembleRandomCoalescingAndFragmentation() {
        Random random = new Random(20261001L);
        List<byte[]> expected = new ArrayList<>();
        Buffer stream = Buffer.buffer();
        for (int index = 0; index < 20; index++) {
            byte[] value = frame(
                    index + 1L,
                    new byte[random.nextInt(256)]);
            expected.add(value);
            stream.appendBytes(value);
        }

        FrameAccumulator accumulator =
                new FrameAccumulator(1024);
        List<byte[]> decoded = new ArrayList<>();
        int offset = 0;
        while (offset < stream.length()) {
            int remaining = stream.length() - offset;
            int length = Math.min(
                    remaining,
                    1 + random.nextInt(
                            Math.min(remaining, 131)));
            accumulator.accept(
                    stream.getBuffer(
                            offset,
                            offset + length),
                    decoded::add);
            offset += length;
        }

        assertEquals(expected.size(), decoded.size());
        for (int index = 0;
                index < expected.size();
                index++) {
            assertArrayEquals(
                    expected.get(index),
                    decoded.get(index));
        }
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
