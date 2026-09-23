package io.peach.rpc.codec.fory;

import io.peach.rpc.codec.RpcCodec;
import io.peach.rpc.codec.RpcCodecIds;
import io.peach.rpc.spi.Extension;
import org.apache.fory.Fory;
import org.apache.fory.ThreadSafeFory;

/**
 * Apache Fory 高性能 Java 对象编解码器。
 *
 * <p>当前 V2-A 仍使用 Fory 通用对象图能力；后续 V2-B 会基于服务契约扫描类型并开启显式注册。
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
    public <T> T decode(byte[] bytes, Class<T> type) {
        Object value = fory.deserialize(bytes);
        return type.cast(value);
    }
}
