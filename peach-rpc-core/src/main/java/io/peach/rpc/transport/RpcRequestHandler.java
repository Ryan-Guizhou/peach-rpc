package io.peach.rpc.transport;

import io.peach.rpc.api.RpcEndpoint;
import java.util.concurrent.CompletionStage;

/** Provider 收到完整协议帧后的异步处理器。 */
@FunctionalInterface
public interface RpcRequestHandler {

    /**
     * 处理来自远端连接的完整 RPC 帧。
     *
     * @param remote 远端地址
     * @param frame 完整协议帧字节
     * @return 响应协议帧
     */
    CompletionStage<byte[]> handle(RpcEndpoint remote, byte[] frame);
}
