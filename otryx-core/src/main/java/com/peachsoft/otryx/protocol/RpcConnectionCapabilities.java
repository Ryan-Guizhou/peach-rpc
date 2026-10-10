package io.peach.rpc.protocol;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 连接端声明的协议能力。
 *
 * @param protocolVersions 支持的协议版本
 * @param codecIds 支持的 Codec 编号
 * @param compressionIds 支持的压缩编号
 * @param features 支持的协议特性
 * @param maxFrameBytes 单帧最大字节数
 */
public record RpcConnectionCapabilities(
        Set<Byte> protocolVersions,
        Set<Byte> codecIds,
        Set<Byte> compressionIds,
        Set<RpcFeature> features,
        int maxFrameBytes) {

    /** 校验并固化连接能力。 */
    public RpcConnectionCapabilities {
        protocolVersions = orderedCopy(protocolVersions);
        codecIds = orderedCopy(codecIds);
        compressionIds = orderedCopy(compressionIds);
        features = features == null ? Set.of() : Set.copyOf(features);
        if (protocolVersions.isEmpty()) {
            throw new IllegalArgumentException("At least one protocol version is required");
        }
        if (codecIds.isEmpty()) {
            throw new IllegalArgumentException("At least one codec is required");
        }
        if (compressionIds.isEmpty()) {
            throw new IllegalArgumentException("At least one compression option is required");
        }
        if (maxFrameBytes <= 0) {
            throw new IllegalArgumentException("maxFrameBytes must be positive");
        }
    }

    private static Set<Byte> orderedCopy(Set<Byte> values) {
        return values == null ? Set.of() : java.util.Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }
}
