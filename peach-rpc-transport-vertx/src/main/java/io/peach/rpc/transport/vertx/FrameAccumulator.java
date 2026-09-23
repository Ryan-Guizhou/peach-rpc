package io.peach.rpc.transport.vertx;

import io.peach.rpc.protocol.RpcProtocolCodec;
import io.peach.rpc.protocol.RpcProtocolException;
import io.vertx.core.buffer.Buffer;
import java.util.function.Consumer;

/**
 * TCP 字节流帧重组器，仅在 Vert.x Adapter 内使用。
 *
 * <p>累积 Buffer 始终保持可增长。消费完整帧时仅移动读取偏移；
 * 只有存在未完成尾帧时才压缩剩余字节，避免把 Netty slice
 * 作为下一轮可写 Buffer 使用。
 */
final class FrameAccumulator {

    private final int maxFrameBytes;
    private Buffer pending = Buffer.buffer();

    FrameAccumulator(int maxFrameBytes) {
        this.maxFrameBytes = maxFrameBytes;
    }

    void accept(
            Buffer incoming,
            Consumer<byte[]> consumer) {
        pending.appendBuffer(incoming);
        int readOffset = 0;

        while (pending.length() - readOffset
                >= RpcProtocolCodec.HEADER_LENGTH) {
            int headerEnd =
                    readOffset + RpcProtocolCodec.HEADER_LENGTH;
            byte[] header = pending.getBytes(
                    readOffset,
                    headerEnd);
            int frameLength =
                    RpcProtocolCodec.expectedFrameLength(header);
            if (frameLength > maxFrameBytes) {
                throw new RpcProtocolException(
                        "RPC frame exceeds transport maxFrameBytes");
            }
            if (pending.length() - readOffset < frameLength) {
                break;
            }

            int frameEnd = readOffset + frameLength;
            consumer.accept(pending.getBytes(
                    readOffset,
                    frameEnd));
            readOffset = frameEnd;
        }

        compact(readOffset);
        if (pending.length() > maxFrameBytes) {
            throw new RpcProtocolException(
                    "Buffered partial frame exceeds transport maxFrameBytes");
        }
    }

    private void compact(int readOffset) {
        if (readOffset == 0) {
            return;
        }
        if (readOffset == pending.length()) {
            pending = Buffer.buffer();
            return;
        }
        pending = Buffer.buffer(
                pending.getBytes(
                        readOffset,
                        pending.length()));
    }
}
