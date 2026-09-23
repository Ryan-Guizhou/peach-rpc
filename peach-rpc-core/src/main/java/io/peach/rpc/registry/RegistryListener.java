package io.peach.rpc.registry;

/** 服务实例快照监听器。 */
@FunctionalInterface
public interface RegistryListener {
    /**
     * 接收新的服务实例快照。
     *
     * @param snapshot 服务实例快照
     */
    void onSnapshot(RegistrySnapshot snapshot);
}
