package io.peach.rpc.protocol;

import io.peach.rpc.api.RpcRemoteError;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Peach RPC 框架级远端错误的稳定二进制编解码器。 */
public final class RpcErrorCodec {
    private static final byte VERSION = 1;
    private static final int MAX_FIELD_BYTES = 16 * 1024;

    private RpcErrorCodec() {
    }

    /**
     * 编码框架级远端错误。
     *
     * @param error 远端错误
     * @return 稳定二进制载荷
     */
    public static byte[] encode(RpcRemoteError error) {
        byte[] type = utf8(error.errorType());
        byte[] message = utf8(error.message());
        ByteBuffer buffer = ByteBuffer.allocate(
                        1 + Integer.BYTES + type.length + Integer.BYTES + message.length)
                .order(ByteOrder.BIG_ENDIAN);
        buffer.put(VERSION);
        putField(buffer, type);
        putField(buffer, message);
        return buffer.array();
    }

    /**
     * 解码框架级远端错误。
     *
     * @param payload 错误载荷
     * @return 远端错误
     */
    public static RpcRemoteError decode(byte[] payload) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN);
            if (!buffer.hasRemaining() || buffer.get() != VERSION) {
                throw new RpcProtocolException("Unsupported RPC error payload version");
            }
            String type = readField(buffer);
            String message = readField(buffer);
            if (buffer.hasRemaining()) {
                throw new RpcProtocolException("Unexpected trailing RPC error bytes");
            }
            return new RpcRemoteError(type, message);
        } catch (java.nio.BufferUnderflowException error) {
            throw new RpcProtocolException("Incomplete RPC error payload", error);
        }
    }

    private static byte[] utf8(String value) {
        byte[] bytes = value == null
                ? new byte[0]
                : value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FIELD_BYTES) {
            throw new RpcProtocolException("RPC error field exceeds max length");
        }
        return bytes;
    }

    private static void putField(ByteBuffer buffer, byte[] value) {
        buffer.putInt(value.length);
        buffer.put(value);
    }

    private static String readField(ByteBuffer buffer) {
        int length = buffer.getInt();
        if (length < 0 || length > MAX_FIELD_BYTES || length > buffer.remaining()) {
            throw new RpcProtocolException("Invalid RPC error field length");
        }
        byte[] value = new byte[length];
        buffer.get(value);
        return new String(value, StandardCharsets.UTF_8);
    }
}
