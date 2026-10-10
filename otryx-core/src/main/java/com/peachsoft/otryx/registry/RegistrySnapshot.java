package com.peachsoft.otryx.registry;

import com.peachsoft.otryx.api.ServiceInstance;
import java.util.List;

/**
 * 注册中心服务实例快照。
 *
 * @param instances 当前可用服务实例
 * @param revision 快照单调版本；实现无法提供外部版本时也必须在进程内单调递增
 *
 * @Author Ryan
 * @Version 1.0.0
 * @CreateTime 2026/9/23 10:51
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
