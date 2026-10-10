package com.peachsoft.otryx.protocol;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.peachsoft.otryx.api.RpcStatus;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 协议畸形输入与边界测试。
 *
 * @Author Ryan
 * @Version 1.0.0
 * @CreateTime 2026/9/30 11:19
 */
class RpcProtocolRobustnessTest {

    @Test
    void everyTruncatedFrameShouldBeRejected() {
        byte[] valid = RpcProtocolCodec.encode(
                new RpcFrame(
                        RpcMessageType.REQUEST,
                        (byte) 1,
                        RpcStatus.OK,
                        7L,
                        11,
                        22,
                        Map.of("traceparent", "safe"),
                        new byte[128]));

        for (int length = 0;
                length < valid.length;
                length++) {
            byte[] truncated =
                    Arrays.copyOf(valid, length);
            assertThrows(
                    RpcProtocolException.class,
                    () -> RpcProtocolCodec.view(
                            truncated),
                    "length=" + length);
        }
    }

    @Test
    void malformedHeaderFieldsShouldBeRejected() {
        byte[] valid = RpcProtocolCodec.encode(
                new RpcFrame(
                        RpcMessageType.REQUEST,
                        (byte) 1,
                        RpcStatus.OK,
                        1L,
                        11,
                        22,
                        Map.of(),
                        new byte[] {1, 2, 3}));

        byte[] version = valid.clone();
        version[2] = 99;
        assertThrows(
                RpcProtocolException.class,
                () -> RpcProtocolCodec.view(version));

        byte[] headerLength = valid.clone();
        headerLength[3] = 31;
        assertThrows(
                RpcProtocolException.class,
                () -> RpcProtocolCodec.view(
                        headerLength));

        byte[] messageType = valid.clone();
        messageType[6] = 127;
        assertThrows(
                RpcProtocolException.class,
                () -> RpcProtocolCodec.view(
                        messageType));

        byte[] status = valid.clone();
        status[9] = 127;
        assertThrows(
                RpcProtocolException.class,
                () -> RpcProtocolCodec.view(status));
    }

    @Test
    void reservedFlagsAndUnsupportedCompressionShouldBeRejected() {
        byte[] valid = RpcProtocolCodec.encode(
                new RpcFrame(
                        RpcMessageType.REQUEST,
                        (byte) 1,
                        RpcStatus.OK,
                        1L,
                        11,
                        22,
                        Map.of(),
                        new byte[] {1}));

        byte[] flags = valid.clone();
        flags[4] = 1;
        assertThrows(
                RpcProtocolException.class,
                () -> RpcProtocolCodec.view(flags));

        byte[] compression = valid.clone();
        compression[8] = RpcCompressionIds.LZ4;
        assertThrows(
                RpcProtocolException.class,
                () -> RpcProtocolCodec.view(compression));
    }

    @Test
    void malformedMetadataShouldBeRejectedOnDecode() {
        byte[] valid = RpcProtocolCodec.encode(
                new RpcFrame(
                        RpcMessageType.REQUEST,
                        (byte) 1,
                        RpcStatus.OK,
                        1L,
                        11,
                        22,
                        Map.of("key", "value"),
                        new byte[] {1}));
        byte[] malformed = valid.clone();

        malformed[RpcProtocolCodec.HEADER_LENGTH + 3] =
                (byte) ':';

        assertThrows(
                RpcProtocolException.class,
                () -> RpcProtocolCodec.decode(malformed));
    }

    @Test
    void invalidBodyLengthsShouldBeRejected() {
        byte[] valid = RpcProtocolCodec.encode(
                new RpcFrame(
                        RpcMessageType.REQUEST,
                        (byte) 1,
                        RpcStatus.OK,
                        1L,
                        11,
                        22,
                        Map.of(),
                        new byte[] {1}));

        byte[] negativePayload = valid.clone();
        Arrays.fill(
                negativePayload,
                28,
                32,
                (byte) 0xff);
        assertThrows(
                RpcProtocolException.class,
                () -> RpcProtocolCodec.view(
                        negativePayload));

        byte[] oversizedPayload = valid.clone();
        int oversized =
                RpcProtocolCodec.MAX_BODY_LENGTH + 1;
        oversizedPayload[28] =
                (byte) (oversized >>> 24);
        oversizedPayload[29] =
                (byte) (oversized >>> 16);
        oversizedPayload[30] =
                (byte) (oversized >>> 8);
        oversizedPayload[31] =
                (byte) oversized;
        assertThrows(
                RpcProtocolException.class,
                () -> RpcProtocolCodec.expectedFrameLength(
                        oversizedPayload));
    }
}
