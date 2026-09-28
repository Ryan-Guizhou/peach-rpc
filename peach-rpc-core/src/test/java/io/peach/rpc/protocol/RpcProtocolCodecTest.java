package io.peach.rpc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.peach.rpc.api.RpcStatus;
import io.peach.rpc.codec.RpcCodecIds;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RpcProtocolCodecTest {

    @Test
    void shouldRoundTripFrame() {
        RpcFrame input = new RpcFrame(
                RpcMessageType.REQUEST,
                (byte) 1,
                RpcStatus.OK,
                7L,
                11,
                22,
                Map.of("deadline", "123"),
                new byte[] {1, 2, 3});

        byte[] encoded = RpcProtocolCodec.encode(input);
        int expectedLength = RpcProtocolCodec.expectedFrameLength(
                Arrays.copyOf(encoded, RpcProtocolCodec.HEADER_LENGTH));
        RpcFrame decoded = RpcProtocolCodec.decode(encoded);

        assertEquals(encoded.length, expectedLength);
        assertEquals(input.messageType(), decoded.messageType());
        assertEquals(input.codec(), decoded.codec());
        assertEquals(input.status(), decoded.status());
        assertEquals(input.requestId(), decoded.requestId());
        assertEquals(input.serviceId(), decoded.serviceId());
        assertEquals(input.methodId(), decoded.methodId());
        assertEquals(input.metadata(), decoded.metadata());
        assertArrayEquals(input.payload(), decoded.payload());
    }


    @Test
    void shouldEncodeUnaryRequestWithoutGenericMetadataObjects() {
        long deadline = 2_000_000_000_123L;
        byte[] payload = new byte[] {4, 5, 6};

        byte[] encoded = RpcProtocolCodec.encodeRequest(
                (byte) 1,
                11,
                22,
                deadline,
                payload);
        RpcFrameView view = RpcProtocolCodec.view(encoded);

        assertEquals(RpcMessageType.REQUEST, view.messageType());
        assertEquals(RpcStatus.OK, view.status());
        assertEquals(0L, view.requestId());
        assertEquals(11, view.serviceId());
        assertEquals(22, view.methodId());
        assertEquals(deadline, view.deadlineEpochMillis());
        assertArrayEquals(payload, view.payloadCopy());

        RpcProtocolCodec.writeRequestId(encoded, 99L);
        assertEquals(99L, RpcProtocolCodec.view(encoded).requestId());
    }

    @Test
    void shouldEncodeRelativeTimeoutBudgetAlongsideLegacyDeadline() {
        long deadline = 2_000_000_000_123L;
        long timeoutBudgetMillis = 1500L;

        byte[] encoded = RpcProtocolCodec.encodeRequest(
                (byte) 1,
                11,
                22,
                deadline,
                timeoutBudgetMillis,
                new byte[] {1});
        RpcFrameView view = RpcProtocolCodec.view(encoded);

        assertEquals(deadline, view.deadlineEpochMillis());
        assertEquals(
                timeoutBudgetMillis,
                view.timeoutBudgetMillis());

        assertTrue(
                RpcProtocolCodec.rewriteTimeoutBudgetMillis(
                        encoded,
                        25L));
        assertEquals(
                25L,
                RpcProtocolCodec.view(encoded)
                        .timeoutBudgetMillis());
    }

    @Test
    void shouldEncodeUnaryResponseWithoutMetadata() {
        byte[] payload = new byte[] {7, 8};

        byte[] encoded = RpcProtocolCodec.encodeResponse(
                (byte) 1,
                RpcStatus.OK,
                88L,
                12,
                23,
                payload);
        RpcFrame decoded = RpcProtocolCodec.decode(encoded);

        assertEquals(RpcMessageType.RESPONSE, decoded.messageType());
        assertEquals(88L, decoded.requestId());
        assertEquals(12, decoded.serviceId());
        assertEquals(23, decoded.methodId());
        assertEquals(Map.of(), decoded.metadata());
        assertArrayEquals(payload, decoded.payload());
    }

    @Test
    void shouldEncodeCancelFrame() {
        byte[] encoded = RpcProtocolCodec.encodeCancel(77L);
        RpcFrame decoded = RpcProtocolCodec.decode(encoded);

        assertEquals(RpcMessageType.CANCEL, decoded.messageType());
        assertEquals(77L, decoded.requestId());
        assertEquals(0, decoded.payload().length);
    }

    @Test
    void shouldEncodeHeartbeatFrames() {
        for (RpcMessageType type : new RpcMessageType[]{
                RpcMessageType.PING,
                RpcMessageType.PONG}) {
            RpcFrame decoded = RpcProtocolCodec.decode(
                    RpcProtocolCodec.encodeHeartbeat(type));

            assertEquals(type, decoded.messageType());
            assertEquals(RpcCodecIds.CONTROL, decoded.codec());
            assertEquals(RpcStatus.OK, decoded.status());
            assertEquals(0L, decoded.requestId());
            assertEquals(0, decoded.payload().length);
        }
    }

    @Test
    void shouldRejectNonHeartbeatControlType() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RpcProtocolCodec.encodeHeartbeat(
                        RpcMessageType.CANCEL));
    }

    @Test
    void shouldRejectBrokenMagic() {
        byte[] frame = RpcProtocolCodec.encode(new RpcFrame(
                RpcMessageType.PING,
                (byte) 0,
                RpcStatus.OK,
                1L,
                0,
                0,
                Map.of(),
                new byte[0]));
        frame[0] = 0;

        assertThrows(RpcProtocolException.class, () -> RpcProtocolCodec.decode(frame));
    }
}
