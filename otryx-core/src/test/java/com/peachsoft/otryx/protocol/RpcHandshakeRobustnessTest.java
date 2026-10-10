package com.peachsoft.otryx.protocol;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.peachsoft.otryx.codec.RpcCodecIds;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * HELLO Payload 畸形与截断输入测试。
 *
 * @Author Ryan
 * @Version 1.0.0-SNAPSHOT
 * @CreateTime 2026/9/30 11:19
 */
class RpcHandshakeRobustnessTest {

    @Test
    void everyTruncatedHandshakeShouldBeRejected() {
        RpcConnectionCapabilities capabilities =
                capabilities();
        byte[] encoded =
                RpcHandshakeCodec.encode(capabilities);

        for (int length = 0;
                length < encoded.length;
                length++) {
            byte[] truncated =
                    Arrays.copyOf(encoded, length);
            assertThrows(
                    RpcProtocolException.class,
                    () -> RpcHandshakeCodec.decode(
                            truncated),
                    "length=" + length);
        }
    }

    @Test
    void duplicateCapabilityShouldBeRejected() {
        byte[] encoded = RpcHandshakeCodec.encode(
                capabilities());

        // payload version, protocol count, protocol,
        // codec count, first codec, second codec.
        encoded[5] = encoded[4];

        assertThrows(
                RpcProtocolException.class,
                () -> RpcHandshakeCodec.decode(encoded));
    }

    @Test
    void trailingHandshakeBytesShouldBeRejected() {
        byte[] encoded = RpcHandshakeCodec.encode(
                capabilities());
        byte[] trailing = Arrays.copyOf(
                encoded,
                encoded.length + 1);

        assertThrows(
                RpcProtocolException.class,
                () -> RpcHandshakeCodec.decode(trailing));
    }

    private static RpcConnectionCapabilities capabilities() {
        return new RpcConnectionCapabilities(
                Set.of((byte) 1),
                Set.of(
                        RpcCodecIds.FORY_NATIVE,
                        RpcCodecIds.PROTOBUF),
                Set.of(RpcCompressionIds.NONE),
                Set.of(RpcFeature.CANCEL),
                1024);
    }
}
