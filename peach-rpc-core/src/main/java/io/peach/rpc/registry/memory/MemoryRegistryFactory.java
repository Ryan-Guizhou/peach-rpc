package io.peach.rpc.registry.memory;

import io.peach.rpc.registry.Registry;
import io.peach.rpc.registry.RegistryFactory;
import io.peach.rpc.spi.Extension;
import java.util.Map;

/** 内存注册中心工厂，适合单 JVM 测试和本地开发。 */
@Extension("memory")
public final class MemoryRegistryFactory implements RegistryFactory {

    /**
     * 创建内存注册中心工厂。
     */
    public MemoryRegistryFactory() {
    }

    private static final Registry SHARED = new MemoryRegistry();

    @Override
    public Registry create(Map<String, String> options) {
        return SHARED;
    }
}
