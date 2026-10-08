package io.peach.rpc.codec.fory;

import io.peach.rpc.api.RpcMethodDescriptor;
import io.peach.rpc.api.RpcTypeRegistry;
import io.peach.rpc.codec.RpcCodec;
import io.peach.rpc.codec.RpcCodecIds;
import io.peach.rpc.codec.RpcMethodCodec;
import io.peach.rpc.spi.Extension;
import java.nio.ByteBuffer;
import java.util.Objects;
import org.apache.fory.resolver.AllowListChecker;
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

    private final RpcTypeRegistry typeRegistry =
            new RpcTypeRegistry();
    private final ThreadSafeFory fory;
    private final ForyRpcSecurityOptions securityOptions;

    /** 创建与原有 Wire v1 报文格式兼容的 Fory 编解码器。 */
    public ForyRpcCodec() {
        this(ForyRpcSecurityOptions.trustedCompatibility());
    }

    /**
     * 按指定安全策略创建 Fory 编解码器。
     *
     * @param options 安全策略
     */
    public ForyRpcCodec(ForyRpcSecurityOptions options) {
        securityOptions = Objects.requireNonNull(options, "options");
        var builder = Fory.builder()
                .withXlang(false)
                .requireClassRegistration(false)
                .withMaxDepth(options.maxDepth())
                .withMaxGraphMemoryBytes(options.maxGraphMemoryBytes());
        if (options.mode()
                == ForyRpcSecurityOptions.Mode.STRICT_ALLOWLIST) {
            AllowListChecker checker = new AllowListChecker(
                    AllowListChecker.CheckLevel.STRICT);
            for (String pattern : options.allowedClassPatterns()) {
                checker.allowClass(pattern);
            }
            builder.withTypeChecker(checker);
        }
        fory = builder.buildThreadSafeFory();
    }

    private void checkPayload(int length) {
        if (length < 0 || length > securityOptions.maxPayloadBytes()) {
            throw new IllegalArgumentException(
                    "Fory RPC payload exceeds configured maximum: " + length);
        }
    }

    private byte[] encodeChecked(Object value) {
        byte[] encoded = fory.serialize(value);
        checkPayload(encoded.length);
        return encoded;
    }

    private Object decodeChecked(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        checkPayload(bytes.length);
        return fory.deserialize(bytes);
    }

    private Object decodeSlice(byte[] frame, int offset, int length) {
        Objects.requireNonNull(frame, "frame");
        if (offset < 0 || length < 0 || offset > frame.length - length) {
            throw new IllegalArgumentException("Invalid Fory payload slice");
        }
        checkPayload(length);
        return fory.deserialize(ByteBuffer.wrap(frame, offset, length));
    }

    @Override
    public byte code() {
        return RpcCodecIds.FORY_NATIVE;
    }

    @Override
    public byte[] encode(Object value) {
        return encodeChecked(value);
    }

    @Override
    public <T> T decode(
            byte[] bytes,
            Class<T> type) {
        Object value = decodeChecked(bytes);
        return type.cast(value);
    }

    @Override
    public RpcMethodCodec bind(
            RpcMethodDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        typeRegistry.register(descriptor);
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
            return encodeChecked(
                    arguments == null
                            ? new Object[0]
                            : arguments);
        }

        @Override
        public Object[] decodeArguments(byte[] payload) {
            return (Object[]) decodeChecked(payload);
        }

        @Override
        public Object[] decodeArguments(
                byte[] frame,
                int offset,
                int length) {
            return (Object[]) decodeSlice(frame, offset, length);
        }

        @Override
        public byte[] encodeResult(Object value) {
            return encodeChecked(value);
        }

        @Override
        public Object decodeResult(byte[] payload) {
            return decodeChecked(payload);
        }

        @Override
        public Object decodeResult(
                byte[] frame,
                int offset,
                int length) {
            return decodeSlice(frame, offset, length);
        }
    }
}
