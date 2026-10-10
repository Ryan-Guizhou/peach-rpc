package com.peachsoft.otryx.registry.nacos;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.peachsoft.otryx.api.ServiceKey;
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
