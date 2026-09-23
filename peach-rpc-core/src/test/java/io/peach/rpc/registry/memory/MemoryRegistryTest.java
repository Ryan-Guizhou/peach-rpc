package io.peach.rpc.registry.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.peach.rpc.api.RpcEndpoint;
import io.peach.rpc.api.ServiceInstance;
import io.peach.rpc.api.ServiceKey;
import io.peach.rpc.registry.Registry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class MemoryRegistryTest {
    @Test
    void subscriberShouldObserveRegisterAndUnregister() {
        Registry registry = new MemoryRegistry();
        ServiceKey key = new ServiceKey("demo.Service", "1.0.0", "default");
        ServiceInstance instance = new ServiceInstance(
                "node-1", key, new RpcEndpoint("127.0.0.1", 19090), 100, Map.of());
        List<Integer> sizes = new CopyOnWriteArrayList<>();

        try (var subscription = registry.subscribe(key, snapshot -> sizes.add(snapshot.instances().size()))) {
            registry.register(instance).toCompletableFuture().join();
            registry.unregister(instance).toCompletableFuture().join();
        }

        assertEquals(List.of(0, 1, 0), sizes);
    }
}
