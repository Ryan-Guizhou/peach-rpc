package io.peach.rpc.registry.nacos;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.peach.rpc.api.ServiceKey;
import org.junit.jupiter.api.Test;

class NacosServiceNamesTest {

    @Test
    void shouldUseCanonicalServiceName() {
        ServiceKey key = new ServiceKey(
                "io.peach.demo.GreetingService",
                "1.0.0",
                "default");

        assertEquals(
                "io.peach.demo.GreetingService:1.0.0:default",
                NacosServiceNames.serviceName(key));
    }
}
