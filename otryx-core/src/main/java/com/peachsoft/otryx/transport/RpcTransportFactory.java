package com.peachsoft.otryx.transport;

import com.peachsoft.otryx.spi.SPI;

/**
 * Transport 构造扩展点。
 *
 * @Author Ryan
 * @Version 1.0.0
 * @CreateTime 2026/9/23 10:51
 */
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
