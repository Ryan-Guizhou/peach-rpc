package com.peachsoft.otryx.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * 验证 SPI 加载器的扩展发现、命名和异常处理。
 *
 * @Author Ryan
 * @Version 1.0.0-SNAPSHOT
 * @CreateTime 2026/9/23 10:51
 */
class ExtensionLoaderTest {
    @Test
    void shouldResolveDefaultAndCacheSingleton() {
        ExtensionLoader<TestExtensionPoint> loader = ExtensionLoader.getLoader(TestExtensionPoint.class);
        TestExtensionPoint first = loader.getDefaultExtension();
        TestExtensionPoint second = loader.getExtension("one");
        assertEquals("one", first.value());
        assertSame(first, second);
    }

    @Test
    void shouldRejectUnknownExtension() {
        ExtensionLoader<TestExtensionPoint> loader = ExtensionLoader.getLoader(TestExtensionPoint.class);
        assertThrows(IllegalArgumentException.class, () -> loader.getExtension("missing"));
    }
}
