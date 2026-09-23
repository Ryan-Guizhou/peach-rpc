package io.peach.rpc.protocol;

import java.util.Set;

/**
 * HELLO / HELLO_ACK 协商后的连接能力。
 *
 * @param protocolVersion 协议版本
 * @param codecIds 双端均支持的 Codec
 * @param compressionIds 双端均支持的压缩算法
 * @param features 双端均支持的特性
 * @param maxFrameBytes 双端共同接受的最大帧大小
 */
public record RpcNegotiatedCapabilities(
        byte protocolVersion,
        Set<Byte> codecIds,
        Set<Byte> compressionIds,
        Set<RpcFeature> features,
        int maxFrameBytes) {

    /** 固化不可变协商结果。 */
    public RpcNegotiatedCapabilities {
        codecIds = Set.copyOf(codecIds);
        compressionIds = Set.copyOf(compressionIds);
        features = Set.copyOf(features);
    }
}
