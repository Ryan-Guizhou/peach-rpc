package com.peachsoft.otryx.transport.vertx;

import com.peachsoft.otryx.protocol.RpcProtocolCodec;
import com.peachsoft.otryx.protocol.RpcProtocolException;
import io.vertx.core.buffer.Buffer;
import java.util.function.Consumer;

/**
 * TCP 字节流帧重组器，仅在 Vert.x Adapter 的 connection EventLoop 内调用。
 *
 * <p>当尚无不完整尾帧时，直接从当前入站 Buffer 扫描并复制每个完整帧，
 * 省去先 append 到中间累积 Buffer 的冗余内存拷贝。交付给 RPC Core 的
 * 完整帧仍是独立 byte[]，避免持有可复用的 Transport Buffer。
 *
 * <p>只有 TCP 分片或粘包尾部不完整时才将未消费字节复制到 pending。
 * 固定 32B Header 仍复用数组；已发生分片时沿用原来的追加和压缩策略，
 * 不修改 Wire v1 帧格式，也不改变最大帧大小校验。
 *
 * @Author Ryan
 * @Version 1.0.0
 * @CreateTime 2026/9/23 10:51
 */
final class FrameAccumulator {

    private final int maxFrameBytes;
    private final byte[] header =
            new byte[RpcProtocolCodec.HEADER_LENGTH];
    private Buffer pending = Buffer.buffer();

    /**
     * 创建每条连接的 TCP Frame Accumulator。
     *
     * @param maxFrameBytes 允许的单个完整 Frame 最大字节数
     */
    FrameAccumulator(int maxFrameBytes) {
        if (maxFrameBytes < RpcProtocolCodec.HEADER_LENGTH) {
            throw new IllegalArgumentException(
                    "maxFrameBytes cannot be smaller than a Wire v1 header");
        }
        this.maxFrameBytes = maxFrameBytes;
    }

    /**
     * 消费收到的 TCP 字节，按序向调用方交付独立完整帧数组。
     *
     * @param incoming 当前 Transport 入站 Buffer
     * @param consumer 完整帧接收方
     */
    void accept(
            Buffer incoming,
            Consumer<byte[]> consumer) {
        if (pending.length() == 0) {
            // 常见路径：已对齐的完整 Frame，跳过中间 appendBuffer。
            int consumed = consumeFrames(incoming, consumer);
            if (consumed == 0 && incoming.length() != 0) {
                // 首包只有半帧：复用旧 appendBuffer，避免额外 byte[]。
                if (incoming.length() > maxFrameBytes) {
                    throw new RpcProtocolException(
                            "Buffered partial frame exceeds transport maxFrameBytes");
                }
                pending.appendBuffer(incoming);
                return;
            }
            retainIncompleteTail(incoming, consumed);
            return;
        }

        // 存在旧的不完整帧时仍按既有语义进行拼接与压缩。
        pending.appendBuffer(incoming);
        int consumed = consumeFrames(pending, consumer);
        compact(consumed);
        if (pending.length() > maxFrameBytes) {
            throw new RpcProtocolException(
                    "Buffered partial frame exceeds transport maxFrameBytes");
        }
    }

    private int consumeFrames(
            Buffer source,
            Consumer<byte[]> consumer) {
        int readOffset = 0;
        while (source.length() - readOffset
                >= RpcProtocolCodec.HEADER_LENGTH) {
            int headerEnd =
                    readOffset + RpcProtocolCodec.HEADER_LENGTH;
            source.getBytes(
                    readOffset,
                    headerEnd,
                    header,
                    0);
            int frameLength =
                    RpcProtocolCodec.expectedFrameLength(header);
            if (frameLength > maxFrameBytes) {
                throw new RpcProtocolException(
                        "RPC frame exceeds transport maxFrameBytes");
            }
            if (source.length() - readOffset < frameLength) {
                break;
            }

            int frameEnd = readOffset + frameLength;
            // RPC Core 仍获得独立 byte[]，不暴露 Netty Buffer 所有权。
            consumer.accept(source.getBytes(
                    readOffset,
                    frameEnd));
            readOffset = frameEnd;
        }
        return readOffset;
    }

    private void retainIncompleteTail(
            Buffer source,
            int consumed) {
        int remaining = source.length() - consumed;
        if (remaining == 0) {
            return;
        }
        if (remaining > maxFrameBytes) {
            throw new RpcProtocolException(
                    "Buffered partial frame exceeds transport maxFrameBytes");
        }
        pending = Buffer.buffer(
                source.getBytes(consumed, source.length()));
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
