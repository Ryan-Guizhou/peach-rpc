package io.peach.rpc.transport;

import io.peach.rpc.api.RpcEndpoint;
import java.util.concurrent.CompletionStage;

/** RPC 服务端传输契约。 */
public interface RpcTransportServer extends AutoCloseable {

    /**
     * 启动传输服务端。
     *
     * @param bind 监听地址
     * @param handler 完整 RPC 帧处理器
     * @return 实际监听地址
     */
    CompletionStage<RpcEndpoint> start(RpcEndpoint bind, RpcRequestHandler handler);

    /** 关闭监听和传输层资源。 */
    @Override
    void close();
}
