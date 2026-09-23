package io.peach.rpc.transport;

import io.peach.rpc.api.RpcEndpoint;
import java.time.Duration;
import java.util.concurrent.CompletionStage;

/** RPC 客户端传输契约。 */
public interface RpcTransportClient extends AutoCloseable {

    /**
     * 向指定端点发送一个完整 RPC 帧。
     *
     * <p>Transport 负责在选定连接的 Event Loop 内分配 connection-local
     * Request ID 并写入 Header，Core 不维护跨连接全局序列。
     *
     * @param endpoint 服务端点
     * @param frame 已编码的完整协议帧，Request ID 应保留为 0
     * @param timeout 单次请求超时时间
     * @return 响应协议帧
     */
    CompletionStage<byte[]> request(
            RpcEndpoint endpoint,
            byte[] frame,
            Duration timeout);

    /** 释放连接和传输层资源。 */
    @Override
    void close();
}
