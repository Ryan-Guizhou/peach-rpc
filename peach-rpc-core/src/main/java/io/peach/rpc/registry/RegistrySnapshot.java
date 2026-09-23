package io.peach.rpc.registry;

import io.peach.rpc.api.ServiceInstance;
import java.util.List;

/**
 * 注册中心服务实例快照。
 *
 * @param instances 当前可用服务实例
 * @param revision 快照单调版本；实现无法提供外部版本时也必须在进程内单调递增
 */
public record RegistrySnapshot(List<ServiceInstance> instances, long revision) {

    /** 校验并固化不可变服务实例列表。 */
    public RegistrySnapshot {
        instances = List.copyOf(instances);
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
    }
}
