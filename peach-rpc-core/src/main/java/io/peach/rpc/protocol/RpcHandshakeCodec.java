package io.peach.rpc.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashSet;
import java.util.Set;

/** HELLO / HELLO_ACK 能力载荷编解码与协商工具。 */
public final class RpcHandshakeCodec {

    private static final byte PAYLOAD_VERSION = 1;

    private RpcHandshakeCodec() {
    }

    /**
     * 编码连接能力。
     *
     * @param capabilities 连接能力
     * @return 握手载荷
     */
    public static byte[] encode(RpcConnectionCapabilities capabilities) {
        int size = 1
                + 1 + capabilities.protocolVersions().size()
                + 1 + capabilities.codecIds().size()
                + 1 + capabilities.compressionIds().size()
                + Integer.BYTES
                + Integer.BYTES;
        ByteBuffer buffer = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN);
        buffer.put(PAYLOAD_VERSION);
        putBytes(buffer, capabilities.protocolVersions());
        putBytes(buffer, capabilities.codecIds());
        putBytes(buffer, capabilities.compressionIds());
        buffer.putInt(featureMask(capabilities.features()));
        buffer.putInt(capabilities.maxFrameBytes());
        return buffer.array();
    }

    /**
     * 解码连接能力。
     *
     * @param payload 握手载荷
     * @return 连接能力
     */
    public static RpcConnectionCapabilities decode(byte[] payload) {
        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN);
        if (!buffer.hasRemaining() || buffer.get() != PAYLOAD_VERSION) {
            throw new RpcProtocolException("Unsupported handshake payload version");
        }
        try {
            Set<Byte> versions = readBytes(buffer);
            Set<Byte> codecs = readBytes(buffer);
            Set<Byte> compressions = readBytes(buffer);
            int featureMask = buffer.getInt();
            int maxFrameBytes = buffer.getInt();
            if (buffer.hasRemaining()) {
                throw new RpcProtocolException("Unexpected trailing handshake bytes");
            }
            return new RpcConnectionCapabilities(
                    versions,
                    codecs,
                    compressions,
                    features(featureMask),
                    maxFrameBytes);
        } catch (java.nio.BufferUnderflowException error) {
            throw new RpcProtocolException("Incomplete handshake payload", error);
        }
    }

    /**
     * 协商 Client 与 Server 的公共能力。
     *
     * @param client Client 能力
     * @param server Server 能力
     * @return 协商结果
     */
    public static RpcNegotiatedCapabilities negotiate(
            RpcConnectionCapabilities client,
            RpcConnectionCapabilities server) {
        byte version = highestCommon(client.protocolVersions(), server.protocolVersions());
        Set<Byte> codecs = intersection(client.codecIds(), server.codecIds());
        if (codecs.isEmpty()) {
            throw new RpcProtocolException("No common RPC codec");
        }
        Set<Byte> compressions = intersection(client.compressionIds(), server.compressionIds());
        if (compressions.isEmpty()) {
            throw new RpcProtocolException("No common RPC compression");
        }
        Set<RpcFeature> features = new LinkedHashSet<>(client.features());
        features.retainAll(server.features());
        return new RpcNegotiatedCapabilities(
                version,
                codecs,
                compressions,
                features,
                Math.min(client.maxFrameBytes(), server.maxFrameBytes()));
    }

    private static byte highestCommon(Set<Byte> left, Set<Byte> right) {
        int selected = -1;
        for (byte value : left) {
            int candidate = Byte.toUnsignedInt(value);
            if (right.contains(value) && candidate > selected) {
                selected = candidate;
            }
        }
        if (selected < 0) {
            throw new RpcProtocolException("No common RPC protocol version");
        }
        return (byte) selected;
    }

    private static Set<Byte> intersection(Set<Byte> left, Set<Byte> right) {
        Set<Byte> result = new LinkedHashSet<>();
        for (byte value : left) {
            if (right.contains(value)) {
                result.add(value);
            }
        }
        return Set.copyOf(result);
    }

    private static void putBytes(ByteBuffer buffer, Set<Byte> values) {
        if (values.size() > 255) {
            throw new IllegalArgumentException("Handshake set is too large");
        }
        buffer.put((byte) values.size());
        values.forEach(buffer::put);
    }

    private static Set<Byte> readBytes(ByteBuffer buffer) {
        int size = Byte.toUnsignedInt(buffer.get());
        Set<Byte> values = new LinkedHashSet<>();
        for (int index = 0; index < size; index++) {
            if (!values.add(buffer.get())) {
                throw new RpcProtocolException("Duplicate handshake capability");
            }
        }
        return Set.copyOf(values);
    }

    private static int featureMask(Set<RpcFeature> features) {
        int mask = 0;
        for (RpcFeature feature : features) {
            mask |= feature.mask();
        }
        return mask;
    }

    private static Set<RpcFeature> features(int mask) {
        Set<RpcFeature> result = new LinkedHashSet<>();
        for (RpcFeature feature : RpcFeature.values()) {
            if ((mask & feature.mask()) != 0) {
                result.add(feature);
            }
        }
        return Set.copyOf(result);
    }
}
