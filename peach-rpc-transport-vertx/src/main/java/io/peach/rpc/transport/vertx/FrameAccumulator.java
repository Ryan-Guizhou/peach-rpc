package io.peach.rpc.transport.vertx;

import io.peach.rpc.protocol.RpcProtocolCodec;
import io.peach.rpc.protocol.RpcProtocolException;
import io.vertx.core.buffer.Buffer;
import java.util.function.Consumer;

/** TCP 字节流帧重组器，仅在 Vert.x Adapter 内使用。 */
final class FrameAccumulator {

    private final int maxFrameBytes;
    private Buffer pending = Buffer.buffer();

    FrameAccumulator(int maxFrameBytes) {
        this.maxFrameBytes = maxFrameBytes;
    }

    void accept(Buffer incoming, Consumer<byte[]> consumer) {
        pending.appendBuffer(incoming);
        while (pending.length() >= RpcProtocolCodec.HEADER_LENGTH) {
            byte[] header = pending.getBytes(0, RpcProtocolCodec.HEADER_LENGTH);
            int frameLength = RpcProtocolCodec.expectedFrameLength(header);
            if (frameLength > maxFrameBytes) {
                throw new RpcProtocolException("RPC frame exceeds transport maxFrameBytes");
            }
            if (pending.length() < frameLength) {
                break;
            }
            consumer.accept(pending.getBytes(0, frameLength));
            pending = pending.slice(frameLength, pending.length());
        }

        if (pending.length() > maxFrameBytes) {
            throw new RpcProtocolException("Buffered partial frame exceeds transport maxFrameBytes");
        }
    }
}
