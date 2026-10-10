package com.peachsoft.otryx.proxy;

import com.peachsoft.otryx.spi.SPI;

/**
 * Consumer 代理创建扩展点。
 *
 * @Author Ryan
 * @Version 1.0.0
 * @CreateTime 2026/9/23 10:51
 */
@SPI("jdk")
public interface ProxyFactory {

    /**
     * 创建服务代理。
     *
     * @param serviceType 服务类型
     * @param invocation RPC 调用入口
     * @param <T> 服务类型
     * @return 服务代理
     */
    <T> T create(Class<T> serviceType, RpcInvocation invocation);
}
