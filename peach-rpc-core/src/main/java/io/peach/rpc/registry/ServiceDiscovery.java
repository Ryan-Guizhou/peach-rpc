package io.peach.rpc.registry;

import io.peach.rpc.api.ServiceKey;
import java.util.concurrent.CompletionStage;

/** Consumer 服务发现控制面。 */
public interface ServiceDiscovery {

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
}
