package com.peachsoft.otryx.registry;

import com.peachsoft.otryx.api.ServiceInstance;
import java.util.concurrent.CompletionStage;

/**
 * Provider 服务注册控制面。
 *
 * @Author Ryan
 * @Version 1.0.0
 * @CreateTime 2026/9/23 11:46
 */
public interface ServiceRegistrar {

    /**
     * 注册服务实例。
     *
     * @param instance 服务实例
     * @return 注册完成信号
     */
    CompletionStage<Void> register(ServiceInstance instance);

    /**
     * 注销服务实例。
     *
     * @param instance 服务实例
     * @return 注销完成信号
     */
    CompletionStage<Void> unregister(ServiceInstance instance);
}
