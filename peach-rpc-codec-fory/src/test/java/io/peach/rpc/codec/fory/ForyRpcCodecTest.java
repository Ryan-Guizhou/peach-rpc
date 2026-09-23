package io.peach.rpc.codec.fory;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.peach.rpc.api.RpcMethodDescriptor;
import io.peach.rpc.api.ServiceKey;
import io.peach.rpc.codec.RpcMethodCodec;
import java.lang.reflect.Method;
import java.util.Arrays;
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
