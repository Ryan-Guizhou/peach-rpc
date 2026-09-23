package io.peach.rpc.registry;

import io.peach.rpc.spi.SPI;

/** Registry 构造扩展点。 */
@SPI("memory")
public interface RegistryFactory {

    /**
     * 根据公共 Registry 配置创建 Adapter。
     *
     * @param options Registry 配置
     * @return Registry 实例
     */
    Registry create(RegistryOptions options);
}
