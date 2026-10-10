package com.peachsoft.otryx.registry;

import com.peachsoft.otryx.spi.SPI;

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
