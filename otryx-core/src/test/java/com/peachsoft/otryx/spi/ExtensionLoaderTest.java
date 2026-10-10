package io.peach.rpc.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

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
