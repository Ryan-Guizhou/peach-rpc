package io.peach.rpc.codec;

import io.peach.rpc.spi.ExtensionLoader;
import java.util.HashMap;
import java.util.Map;

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
     * <p>该入口适合嵌入式运行、测试和不希望依赖 ServiceLoader 的场景。
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
            throw new IllegalArgumentException("Unknown codec code: " + code);
        }
        return codec;
    }

    private static void register(Map<Byte, RpcCodec> codecs, RpcCodec codec) {
        RpcCodec previous = codecs.putIfAbsent(codec.code(), codec);
        if (previous != null && previous != codec) {
            throw new IllegalStateException("Duplicate codec code: " + codec.code());
        }
    }

    /**
     * 返回启动阶段已解析的默认 Codec。
     *
     * @return 默认 Codec
     */
    public RpcCodec defaultCodec() {
        return defaultCodec;
    }
}
