package io.peach.rpc.core;

import io.peach.rpc.api.ServiceInstance;
import io.peach.rpc.api.ServiceKey;
import io.peach.rpc.registry.ServiceDiscovery;
import io.peach.rpc.registry.RegistrySnapshot;
import io.peach.rpc.registry.RegistrySubscription;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Consumer 本地服务目录，热路径只读取不可变快照。 */
final class ServiceDirectory implements AutoCloseable {
    private final AtomicReference<RegistrySnapshot> snapshot =
            new AtomicReference<>(new RegistrySnapshot(List.of(), 0));
    private final RegistrySubscription subscription;

    ServiceDirectory(ServiceDiscovery discovery, ServiceKey key) {
        subscription = discovery.subscribe(key, this::publish);
    }

    private void publish(RegistrySnapshot candidate) {
        snapshot.updateAndGet(current ->
                candidate.revision() >= current.revision() ? candidate : current);
    }

    List<ServiceInstance> snapshot() {
        return snapshot.get().instances();
    }

    @Override
    public void close() {
        subscription.close();
    }
}
