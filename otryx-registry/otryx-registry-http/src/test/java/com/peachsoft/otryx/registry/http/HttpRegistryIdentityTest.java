package com.peachsoft.otryx.registry.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.peachsoft.otryx.api.RpcEndpoint;
import com.peachsoft.otryx.api.ServiceInstance;
import com.peachsoft.otryx.api.ServiceKey;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * HTTP Adapter 的服务键、元数据与路由安全约束测试。
 *
 * @Author Ryan
 * @Version 1.0.0
 * @CreateTime 2026/10/10 18:00
 */
class HttpRegistryIdentityTest {

    @Test
    void scopesByNameVersionGroupAndNamespace() {
        ServiceKey base = new ServiceKey("demo.Api", "1.0.0", "blue");
        assertNotEquals(HttpRegistryIdentity.serviceName("a", base),
                HttpRegistryIdentity.serviceName("b", base));
        assertNotEquals(HttpRegistryIdentity.serviceName("a", base),
                HttpRegistryIdentity.serviceName("a",
                        new ServiceKey("demo.Api", "2.0.0", "blue")));
        assertNotEquals(HttpRegistryIdentity.serviceName("a", base),
                HttpRegistryIdentity.serviceName("a",
                        new ServiceKey("demo.Api", "1.0.0", "green")));
    }

    @Test
    void preservesFrozenWireCompatibilityMetadata() {
        ServiceKey key = new ServiceKey("demo.Api", "1.0", "demo");
        Map<String, String> source = Map.of(
                "peach.rpc.protocol.version", "1",
                "peach.rpc.schema.fingerprint", "signature");
        ServiceInstance instance = new ServiceInstance(
                "provider-1", key, new RpcEndpoint("127.0.0.1", 19090), 140, source);
        Map<String, String> metadata = HttpRegistryIdentity.metadata(instance);
        assertEquals("signature", metadata.get("peach.rpc.schema.fingerprint"));
        assertEquals(140, HttpRegistryIdentity.fromRemote(
                key, "127.0.0.1", 19090, metadata).weight());
        assertEquals("provider-1", metadata.get(HttpRegistryIdentity.INSTANCE_ID));
        assertNotEquals(HttpRegistryIdentity.scopedInstanceId("blue", instance),
                HttpRegistryIdentity.scopedInstanceId("green", instance));
    }

    @Test
    void rejectsReservedAndInvalidInstances() {
        ServiceKey key = new ServiceKey("demo.Api", "1.0", "test");
        ServiceInstance bad = new ServiceInstance(
                "a", key, new RpcEndpoint("127.0.0.1", 19090),
                100, Map.of(HttpRegistryIdentity.WEIGHT, "100"));
        assertThrows(IllegalArgumentException.class,
                () -> HttpRegistryIdentity.metadata(bad));
        ServiceInstance unroutable = new ServiceInstance(
                "bad", key, new RpcEndpoint("0.0.0.0", 8080), 100, Map.of());
        assertThrows(IllegalArgumentException.class,
                () -> HttpRegistryIdentity.requireRoutable(unroutable));
        Map<String, String> metadata = new HashMap<>();
        metadata.put(HttpRegistryIdentity.SERVICE_KEY, key.canonicalName());
        metadata.put(HttpRegistryIdentity.INSTANCE_ID, "a");
        metadata.put(HttpRegistryIdentity.WEIGHT, "0");
        assertNull(HttpRegistryIdentity.fromRemote(key, "127.0.0.1", 8080, metadata));
        metadata.put(HttpRegistryIdentity.WEIGHT, "10");
        assertNull(HttpRegistryIdentity.fromRemote(key, "0.0.0.0", 8080, metadata));
        assertNull(HttpRegistryIdentity.fromRemote(
                new ServiceKey("other.Api", "1.0", "test"), "127.0.0.1", 8080, metadata));
    }
}
