package io.peach.rpc.protocol;

import io.peach.rpc.api.RpcStatus;
import java.util.Map;

/**
 * 单个 RPC 线协议帧。
 *
 * @param messageType 消息类型
 * @param codec 编解码器编号
 * @param status RPC 状态
 * @param requestId 连接内请求标识
 * @param serviceId 服务标识
 * @param methodId 方法标识
 * @param metadata 调用元数据
 * @param payload 消息体
 */
public record RpcFrame(
        RpcMessageType messageType,
        byte codec,
        RpcStatus status,
        long requestId,
        int serviceId,
        int methodId,
        Map<String, String> metadata,
        byte[] payload) {

    /** 规范化空 metadata 与 payload，保证下游无需重复判空。 */
    public RpcFrame {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        payload = payload == null ? new byte[0] : payload;
    }
}
