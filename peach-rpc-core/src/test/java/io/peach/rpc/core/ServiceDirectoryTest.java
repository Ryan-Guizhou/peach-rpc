package io.peach.rpc.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.peach.rpc.api.RpcEndpoint;
import io.peach.rpc.api.ServiceInstance;
import io.peach.rpc.api.ServiceKey;
import io.peach.rpc.registry.ServiceDiscovery;
import io.peach.rpc.registry.RegistryListener;
import io.peach.rpc.registry.RegistrySnapshot;
import io.peach.rpc.registry.RegistrySubscription;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ServiceDirectoryTest {

    @Test
    void shouldIgnoreOlderSnapshots() {
        ServiceKey key = new ServiceKey("example.Service", "1.0.0", "default");
        CapturingRegistry registry = new CapturingRegistry();

        try (ServiceDirectory directory = new ServiceDirectory(registry, key)) {
            ServiceInstance newer = instance(key, "newer", 19091);
            ServiceInstance older = instance(key, "older", 19092);

            registry.publish(new RegistrySnapshot(List.of(newer), 20));
            registry.publish(new RegistrySnapshot(List.of(older), 19));

            assertEquals(List.of(newer), directory.snapshot());
        }
    }

    private static ServiceInstance instance(ServiceKey key, String id, int port) {
        return new ServiceInstance(
                id,
                key,
                new RpcEndpoint("127.0.0.1", port),
                100,
                Map.of());
    }

    private static final class CapturingRegistry implements ServiceDiscovery {
        private final AtomicReference<RegistryListener> listener = new AtomicReference<>();

        @Override
        public CompletionStage<RegistrySnapshot> lookup(ServiceKey key) {
            return CompletableFuture.completedFuture(new RegistrySnapshot(List.of(), 0));
        }

        @Override
        public RegistrySubscription subscribe(ServiceKey key, RegistryListener value) {
            listener.set(value);
            return () -> listener.compareAndSet(value, null);
        }

        private void publish(RegistrySnapshot snapshot) {
            RegistryListener current = listener.get();
            if (current != null) {
                current.onSnapshot(snapshot);
            }
        }

    }
}
