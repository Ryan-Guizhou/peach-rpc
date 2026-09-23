package io.peach.rpc.protocol;

import io.peach.rpc.api.RpcStatus;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 完整 RPC 线协议帧的零 body-copy 只读视图。
 *
 * <p>该视图持有原始完整帧 byte[]，Metadata 与 Payload 通过 offset/length
 * 描述。调用方在视图生命周期内不得修改 backing bytes。
 */
public final class RpcFrameView {
    private static final byte[] DEADLINE_KEY =
            "deadlineEpochMillis=".getBytes(StandardCharsets.US_ASCII);

    private final byte[] bytes;
    private final RpcMessageType messageType;
    private final byte codec;
    private final RpcStatus status;
    private final long requestId;
    private final int serviceId;
    private final int methodId;
    private final int metadataOffset;
    private final int metadataLength;
    private final int payloadOffset;
    private final int payloadLength;

    RpcFrameView(
            byte[] bytes,
            RpcMessageType messageType,
            byte codec,
            RpcStatus status,
            long requestId,
            int serviceId,
            int methodId,
            int metadataOffset,
            int metadataLength,
            int payloadOffset,
            int payloadLength) {
        this.bytes = Objects.requireNonNull(bytes, "bytes");
        this.messageType = Objects.requireNonNull(
                messageType,
                "messageType");
        this.codec = codec;
        this.status = Objects.requireNonNull(status, "status");
        this.requestId = requestId;
        this.serviceId = serviceId;
        this.methodId = methodId;
        this.metadataOffset = metadataOffset;
        this.metadataLength = metadataLength;
        this.payloadOffset = payloadOffset;
        this.payloadLength = payloadLength;
    }

    /**
     * 返回消息类型。
     *
     * @return 消息类型
     */
    public RpcMessageType messageType() {
        return messageType;
    }

    /**
     * 返回 Codec 编号。
     *
     * @return Codec 编号
     */
    public byte codec() {
        return codec;
    }

    /**
     * 返回 RPC 状态。
     *
     * @return RPC 状态
     */
    public RpcStatus status() {
        return status;
    }

    /**
     * 返回 Request ID。
     *
     * @return Request ID
     */
    public long requestId() {
        return requestId;
    }

    /**
     * 返回 Service ID。
     *
     * @return Service ID
     */
    public int serviceId() {
        return serviceId;
    }

    /**
     * 返回 Method ID。
     *
     * @return Method ID
     */
    public int methodId() {
        return methodId;
    }

    /**
     * 返回原始完整帧字节。
     *
     * @return 原始完整帧字节
     */
    public byte[] bytes() {
        return bytes;
    }

    /**
     * 返回 Payload 起始 offset。
     *
     * @return Payload 起始 offset
     */
    public int payloadOffset() {
        return payloadOffset;
    }

    /**
     * 返回 Payload 长度。
     *
     * @return Payload 长度
     */
    public int payloadLength() {
        return payloadLength;
    }

    /**
     * 复制 Payload，供兼容 API 使用。
     *
     * @return 独立 Payload 数组
     */
    public byte[] payloadCopy() {
        return java.util.Arrays.copyOfRange(
                bytes,
                payloadOffset,
                payloadOffset + payloadLength);
    }

    /**
     * 直接解析 deadlineEpochMillis Metadata，不构建 Map/String。
     *
     * @return 未携带 Deadline 时返回 0
     */
    public long deadlineEpochMillis() {
        int end = metadataOffset + metadataLength;
        int lineStart = metadataOffset;
        while (lineStart < end) {
            int lineEnd = lineStart;
            while (lineEnd < end && bytes[lineEnd] != '\n') {
                lineEnd++;
            }
            if (matchesDeadlineKey(lineStart, lineEnd)) {
                return parsePositiveLong(
                        lineStart + DEADLINE_KEY.length,
                        lineEnd);
            }
            lineStart = lineEnd + 1;
        }
        return 0L;
    }

    private boolean matchesDeadlineKey(
            int start,
            int end) {
        if (end - start <= DEADLINE_KEY.length) {
            return false;
        }
        for (int index = 0; index < DEADLINE_KEY.length; index++) {
            if (bytes[start + index] != DEADLINE_KEY[index]) {
                return false;
            }
        }
        return true;
    }

    private long parsePositiveLong(
            int start,
            int end) {
        if (start >= end) {
            throw new RpcProtocolException(
                    "Invalid deadline metadata");
        }
        long value = 0L;
        for (int index = start; index < end; index++) {
            int digit = bytes[index] - '0';
            if (digit < 0 || digit > 9) {
                throw new RpcProtocolException(
                        "Invalid deadline metadata");
            }
            if (value > (Long.MAX_VALUE - digit) / 10L) {
                throw new RpcProtocolException(
                        "Deadline metadata overflow");
            }
            value = value * 10L + digit;
        }
        return value;
    }
}
