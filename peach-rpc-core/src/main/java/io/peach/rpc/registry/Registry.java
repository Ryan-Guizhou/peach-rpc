package io.peach.rpc.registry;

import io.peach.rpc.api.ServiceInstance;
import io.peach.rpc.api.ServiceKey;
import java.util.concurrent.CompletionStage;

/** 服务注册与发现控制面。 */
public interface Registry extends AutoCloseable {

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

    /**
     * 获取指定服务的当前快照。
     *
     * @param key 服务唯一键
     * @return 服务实例快照
     */
    CompletionStage<RegistrySnapshot> lookup(ServiceKey key);

    /**
     * 订阅指定服务的快照变化。
     *
     * @param key 服务唯一键
     * @param listener 快照监听器
     * @return 订阅句柄
     */
    RegistrySubscription subscribe(ServiceKey key, RegistryListener listener);

    /** 释放注册中心客户端与订阅资源。 */
    @Override
    void close();
}
