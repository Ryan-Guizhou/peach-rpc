package io.peach.rpc.registry;

import io.peach.rpc.api.ServiceInstance;
import java.util.concurrent.CompletionStage;

/** Provider 服务注册控制面。 */
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
