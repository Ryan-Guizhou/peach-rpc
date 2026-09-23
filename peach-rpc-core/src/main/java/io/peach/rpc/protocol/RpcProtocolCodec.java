package io.peach.rpc.protocol;

import io.peach.rpc.api.RpcStatus;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Peach RPC v1 二进制协议编解码器。 */
public final class RpcProtocolCodec {

    /** Peach RPC 协议魔数。 */
    public static final short MAGIC = (short) 0xCAFE;
    /** 当前协议版本。 */
    public static final byte VERSION = 1;
    /** v1 固定协议头长度。 */
    public static final int HEADER_LENGTH = 32;
    /** v1 metadata 与 payload 合计最大长度。 */
    public static final int MAX_BODY_LENGTH = 16 * 1024 * 1024;

    private static final int METADATA_LENGTH_OFFSET = 26;

    private RpcProtocolCodec() {
    }

    /**
     * 编码完整 RPC 帧。
     *
     * @param frame RPC 帧
     * @return 完整线协议字节
     */
    public static byte[] encode(RpcFrame frame) {
        byte[] metadata = encodeMetadata(frame.metadata());
        int bodyLength = metadata.length + frame.payload().length;
        if (bodyLength > MAX_BODY_LENGTH) {
            throw new RpcProtocolException(
                    "Frame body exceeds max length: " + bodyLength);
        }

        ByteBuffer buffer = ByteBuffer.allocate(HEADER_LENGTH + bodyLength)
                .order(ByteOrder.BIG_ENDIAN);
        buffer.putShort(MAGIC);
        buffer.put(VERSION);
        buffer.put((byte) HEADER_LENGTH);
        buffer.putShort((short) 0); // flags reserved for v1 extensions
        buffer.put(frame.messageType().code());
        buffer.put(frame.codec());
        buffer.put((byte) 0); // compression reserved in v0.1
        buffer.put(frame.status().code());
        buffer.putLong(frame.requestId());
        buffer.putInt(frame.serviceId());
        buffer.putInt(frame.methodId());
        buffer.putShort((short) metadata.length);
        buffer.putInt(frame.payload().length);
        buffer.put(metadata);
        buffer.put(frame.payload());
        return buffer.array();
    }

    /**
     * 解码并校验完整 RPC 帧。
     *
     * @param bytes 完整线协议字节
     * @return 解码后的 RPC 帧
     */
    public static RpcFrame decode(byte[] bytes) {
        if (bytes.length < HEADER_LENGTH) {
            throw new RpcProtocolException("Incomplete RPC header");
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        validatePreamble(buffer);
        buffer.getShort(); // flags
        RpcMessageType messageType = RpcMessageType.fromCode(buffer.get());
        byte codec = buffer.get();
        buffer.get(); // compression

        RpcStatus status;
        try {
            status = RpcStatus.fromCode(buffer.get());
        } catch (IllegalArgumentException error) {
            throw new RpcProtocolException("Invalid status", error);
        }

        long requestId = buffer.getLong();
        int serviceId = buffer.getInt();
        int methodId = buffer.getInt();
        int metadataLength = Short.toUnsignedInt(buffer.getShort());
        int payloadLength = buffer.getInt();
        validateLengths(bytes.length, metadataLength, payloadLength);

        byte[] metadata = new byte[metadataLength];
        byte[] payload = new byte[payloadLength];
        buffer.get(metadata);
        buffer.get(payload);
        return new RpcFrame(
                messageType,
                codec,
                status,
                requestId,
                serviceId,
                methodId,
                decodeMetadata(metadata),
                payload);
    }

    /**
     * 根据固定头计算完整帧长度，用于 TCP 字节流重组。
     *
     * @param header 至少 32 字节的协议头
     * @return 完整帧长度
     */
    public static int expectedFrameLength(byte[] header) {
        if (header.length < HEADER_LENGTH) {
            throw new RpcProtocolException("Incomplete RPC header");
        }
        ByteBuffer buffer = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN);
        validatePreamble(buffer);
        buffer.position(METADATA_LENGTH_OFFSET);
        int metadataLength = Short.toUnsignedInt(buffer.getShort());
        int payloadLength = buffer.getInt();
        validateLengths(-1, metadataLength, payloadLength);
        return HEADER_LENGTH + metadataLength + payloadLength;
    }

    private static void validatePreamble(ByteBuffer buffer) {
        short magic = buffer.getShort();
        byte version = buffer.get();
        int headerLength = Byte.toUnsignedInt(buffer.get());
        if (magic != MAGIC) {
            throw new RpcProtocolException("Invalid magic");
        }
        if (version != VERSION) {
            throw new RpcProtocolException("Unsupported version: " + version);
        }
        if (headerLength != HEADER_LENGTH) {
            throw new RpcProtocolException("Unsupported header length: " + headerLength);
        }
    }

    private static void validateLengths(
            int actualLength, int metadataLength, int payloadLength) {
        if (payloadLength < 0 || metadataLength + (long) payloadLength > MAX_BODY_LENGTH) {
            throw new RpcProtocolException("Invalid body length");
        }
        if (actualLength >= 0
                && actualLength != HEADER_LENGTH + metadataLength + payloadLength) {
            throw new RpcProtocolException("Frame length mismatch");
        }
    }

    private static byte[] encodeMetadata(Map<String, String> metadata) {
        if (metadata.isEmpty()) {
            return new byte[0];
        }
        StringBuilder builder = new StringBuilder();
        metadata.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    validateMetadata(entry.getKey(), entry.getValue());
                    builder.append(entry.getKey())
                            .append('=')
                            .append(entry.getValue())
                            .append('\n');
                });
        byte[] encoded = builder.toString().getBytes(StandardCharsets.UTF_8);
        if (encoded.length > 65_535) {
            throw new RpcProtocolException("Metadata exceeds 65535 bytes");
        }
        return encoded;
    }

    private static void validateMetadata(String key, String value) {
        if (key.indexOf('=') >= 0
                || key.indexOf('\n') >= 0
                || value.indexOf('\n') >= 0) {
            throw new RpcProtocolException("Illegal metadata character");
        }
    }

    private static Map<String, String> decodeMetadata(byte[] bytes) {
        if (bytes.length == 0) {
            return Map.of();
        }
        try {
            Map<String, String> metadata = new LinkedHashMap<>();
            for (String line : new String(bytes, StandardCharsets.UTF_8).split("\\n")) {
                if (line.isEmpty()) {
                    continue;
                }
                int separator = line.indexOf('=');
                if (separator <= 0) {
                    throw new RpcProtocolException("Malformed metadata");
                }
                metadata.put(line.substring(0, separator), line.substring(separator + 1));
            }
            return Map.copyOf(metadata);
        } catch (RuntimeException error) {
            if (error instanceof RpcProtocolException) {
                throw error;
            }
            throw new RpcProtocolException("Failed to decode metadata", error);
        }
    }
}
