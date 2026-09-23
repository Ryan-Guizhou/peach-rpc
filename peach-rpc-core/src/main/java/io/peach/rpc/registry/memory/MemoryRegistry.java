package io.peach.rpc.registry.memory;

import io.peach.rpc.api.ServiceInstance;
import io.peach.rpc.api.ServiceKey;
import io.peach.rpc.registry.Registry;
import io.peach.rpc.registry.RegistryCapabilities;
import io.peach.rpc.registry.RegistryCapability;
import io.peach.rpc.registry.RegistryListener;
import io.peach.rpc.registry.RegistrySnapshot;
import io.peach.rpc.registry.RegistrySubscription;
import io.peach.rpc.registry.ServiceRegistrar;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/** 进程内注册中心。 */
final class MemoryRegistry implements Registry, ServiceRegistrar {
    private static final RegistryCapabilities CAPABILITIES = RegistryCapabilities.of(
            RegistryCapability.REGISTRATION,
            RegistryCapability.SUBSCRIPTION,
            RegistryCapability.REVISION,
            RegistryCapability.WEIGHT,
            RegistryCapability.METADATA);

    private final ConcurrentMap<ServiceKey, ConcurrentMap<String, ServiceInstance>> data =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<ServiceKey, CopyOnWriteArrayList<RegistryListener>> listeners =
            new ConcurrentHashMap<>();
    private final AtomicLong revision = new AtomicLong();

    @Override
    public RegistryCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public CompletionStage<Void> register(ServiceInstance instance) {
        data.computeIfAbsent(instance.serviceKey(), key -> new ConcurrentHashMap<>())
                .put(instance.instanceId(), instance);
        publish(instance.serviceKey());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> unregister(ServiceInstance instance) {
        ConcurrentMap<String, ServiceInstance> instances = data.get(instance.serviceKey());
        if (instances != null) {
            instances.remove(instance.instanceId());
        }
        publish(instance.serviceKey());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<RegistrySnapshot> lookup(ServiceKey key) {
        return CompletableFuture.completedFuture(snapshot(key, revision.get()));
    }

    @Override
    public RegistrySubscription subscribe(ServiceKey key, RegistryListener listener) {
        listeners.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>()).add(listener);
        listener.onSnapshot(snapshot(key, revision.get()));
        return () -> {
            CopyOnWriteArrayList<RegistryListener> current = listeners.get(key);
            if (current != null) {
                current.remove(listener);
            }
        };
    }

    private RegistrySnapshot snapshot(ServiceKey key, long currentRevision) {
        ConcurrentMap<String, ServiceInstance> instances = data.get(key);
        List<ServiceInstance> values = instances == null
                ? List.of()
                : List.copyOf(instances.values());
        return new RegistrySnapshot(values, currentRevision);
    }

    private void publish(ServiceKey key) {
        long currentRevision = revision.incrementAndGet();
        RegistrySnapshot snapshot = snapshot(key, currentRevision);
        for (RegistryListener listener
                : listeners.getOrDefault(key, new CopyOnWriteArrayList<>())) {
            listener.onSnapshot(snapshot);
        }
    }

    @Override
    public void close() {
        data.clear();
        listeners.clear();
    }
}
