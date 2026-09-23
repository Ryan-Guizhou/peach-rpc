package io.peach.rpc.codec;

import io.peach.rpc.api.RpcMethodDescriptor;
import io.peach.rpc.spi.ExtensionLoader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** 已解析 Codec 的只读运行时注册表。 */
public final class RpcCodecRegistry {
    private final Map<Byte, RpcCodec> byCode;
    private final RpcCodec defaultCodec;

    private RpcCodecRegistry(Map<Byte, RpcCodec> byCode, RpcCodec defaultCodec) {
        this.byCode = Map.copyOf(byCode);
        this.defaultCodec = defaultCodec;
    }

    /**
     * 从 SPI 构建一次性 Codec 注册表。
     *
     * @return 已解析的只读 Codec 注册表
     */
    public static RpcCodecRegistry fromSpi() {
        ExtensionLoader<RpcCodec> loader = ExtensionLoader.getLoader(RpcCodec.class);
        Map<Byte, RpcCodec> codecs = new HashMap<>();
        for (String name : loader.names()) {
            RpcCodec codec = loader.getExtension(name);
            register(codecs, codec);
        }
        return new RpcCodecRegistry(codecs, loader.getDefaultExtension());
    }

    /**
     * 使用显式 Codec 集合构建运行时注册表。
     *
     * @param defaultCodec 默认 Codec
     * @param codecs 可用 Codec
     * @return 已解析的只读 Codec 注册表
     */
    public static RpcCodecRegistry of(RpcCodec defaultCodec, RpcCodec... codecs) {
        if (defaultCodec == null) {
            throw new IllegalArgumentException("defaultCodec must not be null");
        }
        Map<Byte, RpcCodec> byCode = new HashMap<>();
        register(byCode, defaultCodec);
        if (codecs != null) {
            for (RpcCodec codec : codecs) {
                if (codec != null) {
                    register(byCode, codec);
                }
            }
        }
        return new RpcCodecRegistry(byCode, defaultCodec);
    }

    /**
     * 根据线协议编号获取 Codec。
     *
     * @param code Codec 编号
     * @return 已解析的 Codec
     */
    public RpcCodec require(byte code) {
        RpcCodec codec = byCode.get(code);
        if (codec == null) {
            throw new IllegalArgumentException("Unknown codec code: " + Byte.toUnsignedInt(code));
        }
        return codec;
    }

    /**
     * 为指定方法绑定目标 Codec。
     *
     * @param descriptor 方法描述
     * @param codecId Codec 编号
     * @return 方法级 Codec
     */
    public RpcMethodCodec bind(RpcMethodDescriptor descriptor, byte codecId) {
        return require(codecId).bind(descriptor);
    }

    /**
     * 返回支持的 Codec 编号。
     *
     * @return 不可变 Codec 编号集合
     */
    public Set<Byte> supportedCodecIds() {
        return byCode.keySet();
    }

    /**
     * 返回启动阶段已解析的默认 Codec。
     *
     * @return 默认 Codec
     */
    public RpcCodec defaultCodec() {
        return defaultCodec;
    }

    private static void register(Map<Byte, RpcCodec> codecs, RpcCodec codec) {
        RpcCodecIds.validateApplicationCodec(codec.code());
        RpcCodec previous = codecs.putIfAbsent(codec.code(), codec);
        if (previous != null && previous != codec) {
            throw new IllegalStateException(
                    "Duplicate codec code: " + Byte.toUnsignedInt(codec.code()));
        }
    }
}
