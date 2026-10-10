package com.peachsoft.otryx.registry;

/**
 * 注册中心订阅句柄，用于显式释放 Watch 或监听资源。
 *
 * @Author Ryan
 * @Version 1.0.0-SNAPSHOT
 * @CreateTime 2026/9/23 10:51
 */
@FunctionalInterface
public interface RegistrySubscription extends AutoCloseable {

    /** 关闭当前订阅。重复调用应当安全。 */
    @Override
    void close();
}
