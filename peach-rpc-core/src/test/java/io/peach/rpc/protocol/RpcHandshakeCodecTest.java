package io.peach.rpc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.peach.rpc.codec.RpcCodecIds;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RpcHandshakeCodecTest {

    @Test
    void shouldRoundTripAndNegotiateCapabilities() {
        RpcConnectionCapabilities client = new RpcConnectionCapabilities(
                ordered((byte) 1, (byte) 2),
                ordered(RpcCodecIds.FORY_NATIVE, RpcCodecIds.PROTOBUF),
                ordered(RpcCompressionIds.NONE, RpcCompressionIds.LZ4),
                Set.of(RpcFeature.DEADLINE, RpcFeature.CANCEL),
                16 * 1024 * 1024);
        RpcConnectionCapabilities server = new RpcConnectionCapabilities(
                ordered((byte) 1),
                ordered(RpcCodecIds.PROTOBUF, RpcCodecIds.FORY_NATIVE),
                ordered(RpcCompressionIds.NONE),
                Set.of(RpcFeature.DEADLINE, RpcFeature.GO_AWAY),
                8 * 1024 * 1024);

        RpcConnectionCapabilities decoded = RpcHandshakeCodec.decode(
                RpcHandshakeCodec.encode(client));
        RpcNegotiatedCapabilities negotiated =
                RpcHandshakeCodec.negotiate(decoded, server);

        assertEquals(client, decoded);
        assertEquals((byte) 1, negotiated.protocolVersion());
        assertEquals(
                Set.of(RpcCodecIds.FORY_NATIVE, RpcCodecIds.PROTOBUF),
                negotiated.codecIds());
        assertEquals(
                Set.of(RpcCompressionIds.NONE),
                negotiated.compressionIds());
        assertEquals(Set.of(RpcFeature.DEADLINE), negotiated.features());
        assertEquals(8 * 1024 * 1024, negotiated.maxFrameBytes());
    }

    @Test
    void shouldRejectWhenNoCommonCodecExists() {
        RpcConnectionCapabilities client = capabilities(
                Set.of(RpcCodecIds.FORY_NATIVE));
        RpcConnectionCapabilities server = capabilities(
                Set.of(RpcCodecIds.PROTOBUF));

        assertThrows(
                RpcProtocolException.class,
                () -> RpcHandshakeCodec.negotiate(client, server));
    }

    @Test
    void shouldRejectWhenNoCommonProtocolVersionExists() {
        RpcConnectionCapabilities client = new RpcConnectionCapabilities(
                Set.of((byte) 1),
                Set.of(RpcCodecIds.FORY_NATIVE),
                Set.of(RpcCompressionIds.NONE),
                Set.of(),
                1024);
        RpcConnectionCapabilities server = new RpcConnectionCapabilities(
                Set.of((byte) 2),
                Set.of(RpcCodecIds.FORY_NATIVE),
                Set.of(RpcCompressionIds.NONE),
                Set.of(),
                1024);

        assertThrows(
                RpcProtocolException.class,
                () -> RpcHandshakeCodec.negotiate(client, server));
    }

    private static RpcConnectionCapabilities capabilities(Set<Byte> codecs) {
        return new RpcConnectionCapabilities(
                Set.of((byte) 1),
                codecs,
                Set.of(RpcCompressionIds.NONE),
                Set.of(),
                1024);
    }

    private static Set<Byte> ordered(byte... values) {
        Set<Byte> result = new LinkedHashSet<>();
        for (byte value : values) {
            result.add(value);
        }
        return result;
    }
}
