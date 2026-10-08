package io.peach.rpc.codec.fory;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.peach.rpc.api.RpcMethodDescriptor;
import io.peach.rpc.api.ServiceKey;
import io.peach.rpc.codec.RpcMethodCodec;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ForyRpcCodecTest {

    @Test
    void shouldDecodeArgumentsAndResultFromFrameSlices()
            throws Exception {
        ForyRpcCodec codec = new ForyRpcCodec();
        Method method = SampleService.class.getMethod(
                "echo",
                String.class);
        RpcMethodDescriptor descriptor = RpcMethodDescriptor.from(
                new ServiceKey(
                        SampleService.class.getName(),
                        "1.0.0",
                        "default"),
                method);
        RpcMethodCodec binding = codec.bind(descriptor);

        byte[] encodedArguments =
                binding.encodeArguments(new Object[] {"value"});
        byte[] argumentFrame = wrap(encodedArguments);
        Object[] decodedArguments = binding.decodeArguments(
                argumentFrame,
                3,
                encodedArguments.length);

        byte[] encodedResult = binding.encodeResult("result");
        byte[] resultFrame = wrap(encodedResult);
        Object decodedResult = binding.decodeResult(
                resultFrame,
                3,
                encodedResult.length);

        assertArrayEquals(
                new Object[] {"value"},
                decodedArguments);
        assertEquals("result", decodedResult);
    }

    @Test
    void strictAllowlistMustAcceptExplicitApplicationContracts() throws Exception {
        ForyRpcCodec strict = new ForyRpcCodec(
                ForyRpcSecurityOptions.strictAllowlist(
                        Set.of("io.peach.rpc.codec.fory.*")));
        Method method = SampleService.class.getMethod(
                "echo", String.class);
        RpcMethodCodec binding = strict.bind(RpcMethodDescriptor.from(
                new ServiceKey(
                        SampleService.class.getName(), "1.0.0", "default"),
                method));
        Object[] args = binding.decodeArguments(
                binding.encodeArguments(new Object[] {"hello"}));
        assertArrayEquals(new Object[] {"hello"}, args);
        assertEquals("ok", binding.decodeResult(
                binding.encodeResult("ok")));
    }

    @Test
    void strictAllowlistMustRejectUnexpectedApplicationTypes() {
        ForyRpcCodec legacy = new ForyRpcCodec();
        ForyRpcCodec strict = new ForyRpcCodec(
                ForyRpcSecurityOptions.strictAllowlist(
                        Set.of("io.peach.rpc.codec.fory.allowed.*")));
        byte[] payload = legacy.encode(new ForbiddenPayload("secret"));
        assertThrows(RuntimeException.class,
                () -> strict.decode(payload, Object.class));
    }

    @Test
    void rejectTooLargePayloadBeforeDeserialization() {
        ForyRpcSecurityOptions security =
                new ForyRpcSecurityOptions(
                        ForyRpcSecurityOptions.Mode.TRUSTED_COMPATIBILITY,
                        Set.of(), 32, 1024 * 1024, 8);
        ForyRpcCodec strictSize = new ForyRpcCodec(security);
        byte[] largePayload = new byte[64];
        assertThrows(IllegalArgumentException.class,
                () -> strictSize.decode(largePayload, Object.class));
        assertThrows(IllegalArgumentException.class,
                () -> strictSize.encode("A message larger than the configured cap"));
    }

    @Test
    void rejectUnsafeOrMissingAllowlistSettings() {
        assertThrows(IllegalArgumentException.class,
                () -> ForyRpcSecurityOptions.strictAllowlist(Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> ForyRpcSecurityOptions.strictAllowlist(Set.of("*")));
        assertThrows(IllegalArgumentException.class,
                () -> ForyRpcSecurityOptions.strictAllowlist(Set.of("java.*")));
        assertTrue(ForyRpcSecurityOptions.trustedCompatibility()
                .maxPayloadBytes() > 0);
    }

    private record ForbiddenPayload(String value) {
    }

    private static byte[] wrap(byte[] payload) {
        byte[] frame = new byte[payload.length + 6];
        Arrays.fill(frame, (byte) 9);
        System.arraycopy(
                payload,
                0,
                frame,
                3,
                payload.length);
        return frame;
    }

    interface SampleService {
        String echo(String value);
    }
}
