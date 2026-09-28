package io.peach.rpc.registry.nacos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.peach.rpc.registry.RegistryCapability;
import io.peach.rpc.registry.RegistryFactory;
import io.peach.rpc.registry.RegistryOptions;
import io.peach.rpc.spi.ExtensionLoader;
import java.util.List;
import java.util.Map;
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
    void shouldRejectEndpointEmbeddedCredentials() {
        RegistryOptions options = new RegistryOptions(
                List.of(
                        "user:secret@127.0.0.1:8848"),
                "public",
                Map.of());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new NacosRegistryFactory()
                        .create(options));

        assertTrue(
                !error.getMessage().contains("secret"));
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
