package io.peach.rpc.registry;

import java.util.Optional;

/**
 * 注册中心适配器统一生命周期入口。
 *
 * <p>所有注册中心必须提供服务发现能力；Provider 主动注册能力是可选的，
 * 用于兼容 Kubernetes EndpointSlice 等 discovery-only 控制面。
 */
public interface Registry extends ServiceDiscovery, AutoCloseable {

    /**
     * 返回适配器能力集合。
     *
     * @return 注册中心能力
     */
    RegistryCapabilities capabilities();

    /**
     * 返回 Provider 注册能力。
     *
     * @return 支持主动注册时返回 Registrar
     */
    default Optional<ServiceRegistrar> registrar() {
        boolean declared = capabilities().supports(
                RegistryCapability.REGISTRATION);
        boolean implemented = this instanceof ServiceRegistrar;
        if (declared != implemented) {
            throw new IllegalStateException(
                    "Registry REGISTRATION capability does not match "
                            + "ServiceRegistrar implementation: "
                            + getClass().getName());
        }
        if (!implemented) {
            return Optional.empty();
        }
        return Optional.of((ServiceRegistrar) this);
    }

    /** 释放注册中心客户端与订阅资源。 */
    @Override
    void close();
}
