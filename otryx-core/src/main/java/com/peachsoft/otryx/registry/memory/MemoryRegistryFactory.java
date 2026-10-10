package com.peachsoft.otryx.registry.memory;

import com.peachsoft.otryx.registry.Registry;
import com.peachsoft.otryx.registry.RegistryFactory;
import com.peachsoft.otryx.registry.RegistryOptions;
import com.peachsoft.otryx.spi.Extension;

/** 内存注册中心工厂，适合单 JVM 测试和本地开发。 */
@Extension("memory")
public final class MemoryRegistryFactory implements RegistryFactory {

    private static final Registry SHARED = new MemoryRegistry();

    /** 创建内存注册中心工厂。 */
    public MemoryRegistryFactory() {
    }

    @Override
    public Registry create(RegistryOptions options) {
        return SHARED;
    }
}
