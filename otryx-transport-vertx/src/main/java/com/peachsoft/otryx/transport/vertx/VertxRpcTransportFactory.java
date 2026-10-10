package io.peach.rpc.transport.vertx;

import io.peach.rpc.spi.Extension;
import io.peach.rpc.transport.RpcTransportClient;
import io.peach.rpc.transport.RpcTransportFactory;
import io.peach.rpc.transport.RpcTransportOptions;
import io.peach.rpc.transport.RpcTransportServer;

/** Vert.x TCP 传输工厂。 */
@Extension("vertx")
public final class VertxRpcTransportFactory implements RpcTransportFactory {

    /**
     * 创建 Vert.x TCP 传输工厂。
     */
    public VertxRpcTransportFactory() {
    }

    @Override
    public RpcTransportClient createClient(RpcTransportOptions options) {
        return new VertxRpcTransportClient(options);
    }

    @Override
    public RpcTransportServer createServer(RpcTransportOptions options) {
        return new VertxRpcTransportServer(options);
    }
}
