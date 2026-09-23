package io.peach.rpc.codec.fory;

import io.peach.rpc.codec.RpcCodec;
import io.peach.rpc.spi.Extension;
import org.apache.fory.Fory;
import org.apache.fory.ThreadSafeFory;

/**
 * Apache Fory 高性能 Java 对象编解码器。
 *
 * <p>内部复用线程安全的 Fory 实例，避免为单次 RPC 调用重复创建序列化器。
 */
@Extension("fory")
public final class ForyRpcCodec implements RpcCodec {

    /**
     * 创建 Fory RPC 编解码器。
     */
    public ForyRpcCodec() {
    }

    private final ThreadSafeFory fory = Fory.builder()
            .withXlang(false)
            .requireClassRegistration(false)
            .buildThreadSafeFory();

    @Override
    public byte code() {
        return 1;
    }

    @Override
    public byte[] encode(Object value) {
        return fory.serialize(value);
    }

    @Override
    public <T> T decode(byte[] bytes, Class<T> type) {
        Object value = fory.deserialize(bytes);
        return type.cast(value);
    }
}
