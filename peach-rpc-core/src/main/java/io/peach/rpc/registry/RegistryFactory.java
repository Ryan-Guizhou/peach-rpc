package io.peach.rpc.registry;

import io.peach.rpc.spi.SPI;
import java.util.Map;

/** Registry 构造扩展点。 */
@SPI("memory")
public interface RegistryFactory {

    /**
     * 根据适配器配置创建 Registry。
     *
     * @param options 适配器配置
     * @return Registry 实例
     */
    Registry create(Map<String, String> options);
}
