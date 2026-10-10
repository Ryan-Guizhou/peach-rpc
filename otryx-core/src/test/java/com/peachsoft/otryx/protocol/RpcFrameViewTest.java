package com.peachsoft.otryx.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.peachsoft.otryx.api.RpcStatus;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 验证 RPC Frame 视图的读取和缓冲区边界。
 *
 * @Author Ryan
 * @Version 1.0.0-SNAPSHOT
 * @CreateTime 2026/9/23 15:46
 */
class RpcFrameViewTest {

    @Test
    void shouldReadHeaderDeadlineAndPayloadWithoutBodyDecode() {
        byte[] payload = new byte[] {4, 5, 6};
        byte[] encoded = RpcProtocolCodec.encode(new RpcFrame(
                RpcMessageType.REQUEST,
                (byte) 1,
                RpcStatus.OK,
                33L,
                100,
                200,
                Map.of("deadlineEpochMillis", "123456789"),
                payload));

        RpcFrameView view = RpcProtocolCodec.view(encoded);

        assertEquals(RpcMessageType.REQUEST, view.messageType());
        assertEquals((byte) 1, view.codec());
        assertEquals(33L, view.requestId());
        assertEquals(100, view.serviceId());
        assertEquals(200, view.methodId());
        assertEquals(123456789L, view.deadlineEpochMillis());
        assertArrayEquals(payload, view.payloadCopy());
    }

    @Test
    void shouldReturnZeroWhenDeadlineIsMissing() {
        byte[] encoded = RpcProtocolCodec.encode(new RpcFrame(
                RpcMessageType.REQUEST,
                (byte) 1,
                RpcStatus.OK,
                1L,
                1,
                1,
                Map.of(),
                new byte[] {1}));

        assertEquals(
                0L,
                RpcProtocolCodec.view(encoded)
                        .deadlineEpochMillis());
    }

    @Test
    void shouldRejectMalformedDeadlineWithoutBuildingMetadataMap() {
        byte[] encoded = RpcProtocolCodec.encode(new RpcFrame(
                RpcMessageType.REQUEST,
                (byte) 1,
                RpcStatus.OK,
                1L,
                1,
                1,
                Map.of("deadlineEpochMillis", "bad"),
                new byte[] {1}));

        assertThrows(
                RpcProtocolException.class,
                () -> RpcProtocolCodec.view(encoded)
                        .deadlineEpochMillis());
    }
}
