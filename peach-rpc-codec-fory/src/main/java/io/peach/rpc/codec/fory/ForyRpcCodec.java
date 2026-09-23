package io.peach.rpc.codec.fory;

import io.peach.rpc.api.RpcMethodDescriptor;
import io.peach.rpc.codec.RpcCodec;
import io.peach.rpc.codec.RpcCodecIds;
import io.peach.rpc.codec.RpcMethodCodec;
import io.peach.rpc.spi.Extension;
import java.nio.ByteBuffer;
import java.util.Objects;
import org.apache.fory.Fory;
import org.apache.fory.ThreadSafeFory;

/**
 * Apache Fory 高性能 Java 对象编解码器。
 *
 * <p>Codec ID 1 的 wire payload 与 V2-A 保持兼容。V2-B 的方法级绑定
 * 只优化本机解码路径，不改变参数在线上的 Object[] 表示。
 */
@Extension("fory")
public final class ForyRpcCodec implements RpcCodec {

    private final ThreadSafeFory fory = Fory.builder()
            .withXlang(false)
            .requireClassRegistration(false)
            .buildThreadSafeFory();

    /** 创建 Fory RPC 编解码器。 */
    public ForyRpcCodec() {
    }

    @Override
    public byte code() {
        return RpcCodecIds.FORY_NATIVE;
    }

    @Override
    public byte[] encode(Object value) {
        return fory.serialize(value);
    }

    @Override
    public <T> T decode(
            byte[] bytes,
            Class<T> type) {
        Object value = fory.deserialize(bytes);
        return type.cast(value);
    }

    @Override
    public RpcMethodCodec bind(
            RpcMethodDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        return new ForyMethodCodec();
    }

    private final class ForyMethodCodec
            implements RpcMethodCodec {

        @Override
        public byte codecId() {
            return RpcCodecIds.FORY_NATIVE;
        }

        @Override
        public byte[] encodeArguments(Object[] arguments) {
            return fory.serialize(
                    arguments == null
                            ? new Object[0]
                            : arguments);
        }

        @Override
        public Object[] decodeArguments(byte[] payload) {
            return (Object[]) fory.deserialize(payload);
        }

        @Override
        public Object[] decodeArguments(
                byte[] frame,
                int offset,
                int length) {
            ByteBuffer payload = ByteBuffer.wrap(
                    frame,
                    offset,
                    length);
            return (Object[]) fory.deserialize(payload);
        }

        @Override
        public byte[] encodeResult(Object value) {
            return fory.serialize(value);
        }

        @Override
        public Object decodeResult(byte[] payload) {
            return fory.deserialize(payload);
        }

        @Override
        public Object decodeResult(
                byte[] frame,
                int offset,
                int length) {
            ByteBuffer payload = ByteBuffer.wrap(
                    frame,
                    offset,
                    length);
            return fory.deserialize(payload);
        }
    }
}
