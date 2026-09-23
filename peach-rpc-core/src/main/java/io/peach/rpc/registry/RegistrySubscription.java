package io.peach.rpc.registry;

/** 注册中心订阅句柄，用于显式释放 Watch 或监听资源。 */
@FunctionalInterface
public interface RegistrySubscription extends AutoCloseable {

    /** 关闭当前订阅。重复调用应当安全。 */
    @Override
    void close();
}
