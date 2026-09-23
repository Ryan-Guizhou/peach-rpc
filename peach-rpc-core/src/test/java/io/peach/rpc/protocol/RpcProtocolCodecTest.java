package io.peach.rpc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.peach.rpc.api.RpcStatus;
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
