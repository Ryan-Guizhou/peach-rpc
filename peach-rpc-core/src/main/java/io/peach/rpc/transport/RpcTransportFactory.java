package io.peach.rpc.transport;

import io.peach.rpc.spi.SPI;

/** Transport 构造扩展点。 */
@SPI("vertx")
public interface RpcTransportFactory {

    /**
     * 创建 Consumer 传输客户端。
     *
     * @param options 传输容量与超时配置
     * @return 传输客户端
     */
    RpcTransportClient createClient(RpcTransportOptions options);

    /**
     * 创建 Provider 传输服务端。
     *
     * @param options 传输容量与超时配置
     * @return 传输服务端
     */
    RpcTransportServer createServer(RpcTransportOptions options);
}
