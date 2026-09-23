package io.peach.rpc.core;

import io.peach.rpc.api.ServiceInstance;
import io.peach.rpc.api.ServiceKey;
import io.peach.rpc.registry.ServiceDiscovery;
import io.peach.rpc.registry.RegistrySnapshot;
import io.peach.rpc.registry.RegistrySubscription;
import java.util.concurrent.atomic.AtomicReference;

/** Consumer 本地服务目录，热路径只读取不可变数组快照。 */
final class ServiceDirectory implements AutoCloseable {

    private final AtomicReference<DirectorySnapshot> snapshot =
            new AtomicReference<>(new DirectorySnapshot(
                    new ServiceInstance[0],
                    0));
    private final RegistrySubscription subscription;

    ServiceDirectory(
            ServiceDiscovery discovery,
            ServiceKey key) {
        subscription = discovery.subscribe(
                key,
                this::publish);
    }

    private void publish(RegistrySnapshot candidate) {
        snapshot.updateAndGet(current -> {
            if (candidate.revision() < current.revision()) {
                return current;
            }
            return new DirectorySnapshot(
                    candidate.instances().toArray(ServiceInstance[]::new),
                    candidate.revision());
        });
    }

    ServiceInstance[] snapshot() {
        return snapshot.get().instances();
    }

    @Override
    public void close() {
        subscription.close();
    }

    private record DirectorySnapshot(
            ServiceInstance[] instances,
            long revision) {
    }
}
