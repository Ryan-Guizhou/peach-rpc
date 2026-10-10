package com.peachsoft.otryx.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.peachsoft.otryx.api.RpcRemoteError;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * 验证 RPC 错误载荷的编解码及非法输入处理。
 *
 * @Author Ryan
 * @Version 1.0.0-SNAPSHOT
 * @CreateTime 2026/9/23 11:46
 */
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
