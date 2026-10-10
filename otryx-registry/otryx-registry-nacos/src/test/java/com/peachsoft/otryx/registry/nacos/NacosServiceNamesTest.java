package com.peachsoft.otryx.registry.nacos;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.peachsoft.otryx.api.ServiceKey;
import org.junit.jupiter.api.Test;

/**
 * 验证 Nacos 服务名的编码、解析与非法参数处理。
 *
 * @Author Ryan
 * @Version 1.0.0
 * @CreateTime 2026/9/28 11:35
 */
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
