package com.peachsoft.otryx.registry;

/**
 * 服务实例快照监听器。
 *
 * @Author Ryan
 * @Version 1.0.0-SNAPSHOT
 * @CreateTime 2026/9/23 10:51
 */
@FunctionalInterface
public interface RegistryListener {
    /**
     * 接收新的服务实例快照。
     *
     * @param snapshot 服务实例快照
     */
    void onSnapshot(RegistrySnapshot snapshot);
}
