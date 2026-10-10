package com.peachsoft.otryx.registry.http;

import com.peachsoft.otryx.api.ServiceInstance;
import com.peachsoft.otryx.api.ServiceKey;
import java.util.List;

/**
 * 基于 HTTP 的注册中心控制面协议适配接口。
 *
 * @Author Ryan
 * @Version 1.0.0
 * @CreateTime 2026/10/10 18:00
 */
public interface HttpRegistryBackend extends AutoCloseable {

    /**
     * 返回相应配置或运行状态。
     *
     * @return 注册中心类型名称
     */
    String type();

    /**
     * 创建或更新 Provider 注册记录。
     *
     * @param instance Provider 实例
     * @throws Exception 控制面操作失败
     */
    void register(ServiceInstance instance) throws Exception;

    /**
     * 续约 Provider 健康租约；远端记录丢失时应重新注册。
     *
     * @param instance Provider 实例
     * @throws Exception 控制面操作失败
     */
    void renew(ServiceInstance instance) throws Exception;

    /**
     * 注销实例。
     *
     * @param instance Provider 实例
     * @throws Exception 控制面操作失败
     */
    void unregister(ServiceInstance instance) throws Exception;

    /**
     * 读取当前可路由实例。
     *
     * @param key 完整 RPC 服务键
     * @return 已过滤、可路由实例
     * @throws Exception 控制面操作失败
     */
    List<ServiceInstance> lookup(ServiceKey key) throws Exception;

    /** 释放 HTTP 客户端资源。 */
    @Override
    void close();
}
