package io.peach.rpc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.peach.rpc.api.RpcRemoteError;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class RpcErrorCodecTest {

    @Test
    void shouldRoundTripRemoteError() {
        RpcRemoteError input = new RpcRemoteError(
                "java.lang.IllegalStateException",
                "Remote service invocation failed");

        RpcRemoteError decoded = RpcErrorCodec.decode(
                RpcErrorCodec.encode(input));

        assertEquals(input, decoded);
    }

    @Test
    void shouldRejectTruncatedRemoteError() {
        byte[] encoded = RpcErrorCodec.encode(
                new RpcRemoteError("demo.Error", "failed"));
        byte[] truncated = Arrays.copyOf(encoded, encoded.length - 1);

        assertThrows(
                RpcProtocolException.class,
                () -> RpcErrorCodec.decode(truncated));
    }
}
