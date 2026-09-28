package io.peach.rpc.registry.nacos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.peach.rpc.registry.RegistryCapability;
import io.peach.rpc.registry.RegistryFactory;
import io.peach.rpc.spi.ExtensionLoader;
import org.junit.jupiter.api.Test;

class NacosRegistryFactoryTest {

    @Test
    void shouldLoadFactoryThroughSpi() {
        RegistryFactory factory =
                ExtensionLoader.getLoader(
                                RegistryFactory.class)
                        .getExtension("nacos");

        assertEquals(
                NacosRegistryFactory.class,
                factory.getClass());
    }

    @Test
    void shouldDeclareExpectedCapabilities() {
        assertTrue(
                NacosRegistry.CAPABILITIES_FOR_TEST
                        .supports(
                                RegistryCapability.REGISTRATION));
        assertTrue(
                NacosRegistry.CAPABILITIES_FOR_TEST
                        .supports(
                                RegistryCapability.SUBSCRIPTION));
    }
}
