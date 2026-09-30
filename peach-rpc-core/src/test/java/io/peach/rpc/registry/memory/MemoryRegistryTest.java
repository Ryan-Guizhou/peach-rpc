package io.peach.rpc.registry.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.peach.rpc.api.RpcEndpoint;
import io.peach.rpc.api.ServiceInstance;
import io.peach.rpc.api.ServiceKey;
import io.peach.rpc.registry.Registry;
import io.peach.rpc.registry.RegistryCapability;
import io.peach.rpc.registry.RegistryContractTestKit;
import io.peach.rpc.registry.ServiceRegistrar;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class MemoryRegistryTest {

    @Test
    void shouldSatisfySharedRegistryContract()
            throws Exception {
        try (Registry registry = new MemoryRegistry()) {
            ServiceKey key = new ServiceKey(
                    "demo.Contract",
                    "1.0.0",
                    "memory");
            ServiceInstance first = new ServiceInstance(
                    "memory-a",
                    key,
                    new RpcEndpoint(
                            "127.0.0.1",
                            19090),
                    100,
                    Map.of());
            ServiceInstance second = new ServiceInstance(
                    "memory-b",
                    key,
                    new RpcEndpoint(
                            "127.0.0.1",
                            19091),
                    100,
                    Map.of());

            RegistryContractTestKit
                    .verifyRegistrationDiscoverySubscription(
                            registry,
                            key,
                            first,
                            Duration.ofSeconds(2));
        }
    }

    @Test
    void subscriberShouldObserveRegisterAndUnregister() {
        Registry registry = new MemoryRegistry();
        ServiceRegistrar registrar = registry.registrar().orElseThrow();
        ServiceKey key = new ServiceKey("demo.Service", "1.0.0", "default");
        ServiceInstance instance = new ServiceInstance(
                "node-1", key, new RpcEndpoint("127.0.0.1", 19090), 100, Map.of());
        List<Integer> sizes = new CopyOnWriteArrayList<>();

        try (var subscription = registry.subscribe(key, snapshot -> sizes.add(snapshot.instances().size()))) {
            registrar.register(instance).toCompletableFuture().join();
            registrar.unregister(instance).toCompletableFuture().join();
        }

        assertEquals(List.of(0, 1, 0), sizes);
        assertTrue(registry.capabilities().supports(RegistryCapability.REGISTRATION));
        assertTrue(registry.capabilities().supports(RegistryCapability.SUBSCRIPTION));
        assertTrue(registry.capabilities().supports(RegistryCapability.REVISION));
    }
}
