package io.peach.rpc.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.peach.rpc.api.RpcMethodDescriptor;
import io.peach.rpc.api.ServiceKey;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class RpcCodecRegistryTest {

    @Test
    void shouldRejectControlCodecId() {
        TestCodec codec = new TestCodec(RpcCodecIds.CONTROL);

        assertThrows(
                IllegalArgumentException.class,
                () -> RpcCodecRegistry.of(codec));
    }

    @Test
    void shouldRejectDuplicateCodecId() {
        TestCodec first = new TestCodec((byte) 42);
        TestCodec second = new TestCodec((byte) 42);

        assertThrows(
                IllegalStateException.class,
                () -> RpcCodecRegistry.of(first, second));
    }

    @Test
    void shouldExposeSupportedCodecIds() {
        TestCodec first = new TestCodec((byte) 42);
        TestCodec second = new TestCodec((byte) 43);

        RpcCodecRegistry registry = RpcCodecRegistry.of(first, second);

        assertEquals(
                java.util.Set.of((byte) 42, (byte) 43),
                registry.supportedCodecIds());
    }

    @Test
    void shouldBindCodecAtMethodLevel() throws Exception {
        BindingCodec codec = new BindingCodec((byte) 44);
        Method method = SampleService.class.getMethod("echo", String.class);
        RpcMethodDescriptor descriptor = RpcMethodDescriptor.from(
                new ServiceKey(SampleService.class.getName(), "1.0.0", "default"),
                method);
        RpcCodecRegistry registry = RpcCodecRegistry.of(codec);

        RpcMethodCodec binding = registry.bind(descriptor, codec.code());

        assertSame(codec.binding, binding);
    }

    private interface SampleService {
        String echo(String value);
    }

    private static final class BindingCodec implements RpcCodec {
        private final byte code;
        private final RpcMethodCodec binding = new DefaultBinding();

        private BindingCodec(byte code) {
            this.code = code;
        }

        @Override
        public byte code() {
            return code;
        }

        @Override
        public byte[] encode(Object value) {
            return new byte[0];
        }

        @Override
        public <T> T decode(byte[] bytes, Class<T> type) {
            return null;
        }

        @Override
        public RpcMethodCodec bind(RpcMethodDescriptor descriptor) {
            return binding;
        }
    }

    private static final class DefaultBinding implements RpcMethodCodec {
        @Override
        public byte codecId() {
            return 44;
        }

        @Override
        public byte[] encodeArguments(Object[] arguments) {
            return new byte[0];
        }

        @Override
        public Object[] decodeArguments(byte[] payload) {
            return new Object[0];
        }

        @Override
        public byte[] encodeResult(Object value) {
            return new byte[0];
        }

        @Override
        public Object decodeResult(byte[] payload) {
            return null;
        }

    }

    private record TestCodec(byte code) implements RpcCodec {
        @Override
        public byte[] encode(Object value) {
            return new byte[0];
        }

        @Override
        public <T> T decode(byte[] bytes, Class<T> type) {
            return null;
        }
    }
}
