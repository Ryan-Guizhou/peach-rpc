package io.peach.rpc.registry.etcd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.etcd.jetcd.test.EtcdClusterExtension;
import io.peach.rpc.api.RpcEndpoint;
import io.peach.rpc.api.ServiceInstance;
import io.peach.rpc.api.ServiceKey;
import io.peach.rpc.registry.RegistrySnapshot;
import io.peach.rpc.registry.RegistrySubscription;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class EtcdRegistryIntegrationTest {

    @RegisterExtension
    static final EtcdClusterExtension CLUSTER =
            EtcdClusterExtension.builder()
                    .withClusterName("peach-rpc-etcd-it")
                    .withNodes(1)
                    .withSsl(false)
                    .build();

    @Test
    void shouldRegisterLookupAndUnregisterAgainstRealEtcd() {
        try (EtcdRegistry registry = registry(uniqueNamespace("roundtrip"), 5)) {
            ServiceKey key =
                    new ServiceKey("demo.RoundTrip", "1.0.0", "default");
            ServiceInstance instance = instance(key, "node-1", 19090);

            registry.register(instance).toCompletableFuture().join();
            RegistrySnapshot registered =
                    registry.lookup(key).toCompletableFuture().join();

            assertEquals(List.of(instance), registered.instances());
            assertTrue(registered.revision() > 0L);

            registry.unregister(instance).toCompletableFuture().join();
            assertTrue(registry.lookup(key)
                    .toCompletableFuture()
                    .join()
                    .instances()
                    .isEmpty());
        }
    }

    @Test
    void shouldPublishWatchSnapshotsWithIncreasingRevision()
            throws Exception {
        try (EtcdRegistry registry = registry(uniqueNamespace("watch"), 5)) {
            ServiceKey key =
                    new ServiceKey("demo.Watch", "1.0.0", "default");
            ServiceInstance instance = instance(key, "node-1", 19091);
            CountDownLatch nonEmpty = new CountDownLatch(1);
            AtomicReference<RegistrySnapshot> latest =
                    new AtomicReference<>();

            try (RegistrySubscription ignored =
                         registry.subscribe(key, snapshot -> {
                             latest.set(snapshot);
                             if (!snapshot.instances().isEmpty()) {
                                 nonEmpty.countDown();
                             }
                         })) {
                registry.register(instance)
                        .toCompletableFuture()
                        .join();

                assertTrue(nonEmpty.await(10, TimeUnit.SECONDS));
                assertEquals(
                        List.of(instance),
                        latest.get().instances());
                assertTrue(latest.get().revision() > 0L);
            }
        }
    }

    @Test
    void shouldIsolateRegistryNamespaces() {
        try (EtcdRegistry left = registry(uniqueNamespace("namespace-a"), 5);
             EtcdRegistry right = registry(uniqueNamespace("namespace-b"), 5)) {
            ServiceKey key =
                    new ServiceKey("demo.Namespace", "1.0.0", "default");
            ServiceInstance instance = instance(key, "node-1", 19092);

            left.register(instance).toCompletableFuture().join();

            assertEquals(
                    List.of(instance),
                    left.lookup(key).toCompletableFuture().join().instances());
            assertTrue(
                    right.lookup(key).toCompletableFuture().join().instances().isEmpty());
        }
    }

    @Test
    void shouldRemoveRegistrationAfterLeaseExpires()
            throws Exception {
        ServiceKey key =
                new ServiceKey("demo.Lease", "1.0.0", "default");
        ServiceInstance instance = instance(key, "node-lease", 19093);
        String namespace = uniqueNamespace("lease");
        EtcdRegistry writer = registry(namespace, 1);

        try (EtcdRegistry observer = registry(namespace, 5)) {
            writer.register(instance).toCompletableFuture().join();
            assertEquals(
                    List.of(instance),
                    observer.lookup(key).toCompletableFuture().join().instances());

            writer.close();

            assertTrue(await(
                    Duration.ofSeconds(8),
                    () -> observer.lookup(key)
                            .toCompletableFuture()
                            .join()
                            .instances()
                            .isEmpty()));
        } finally {
            writer.close();
        }
    }

    private static EtcdRegistry registry(
            String namespace,
            long leaseTtlSeconds) {
        String[] endpoints = CLUSTER.clientEndpoints().stream()
                .map(URI::toString)
                .toArray(String[]::new);
        return new EtcdRegistry(
                endpoints,
                leaseTtlSeconds,
                namespace);
    }

    private static String uniqueNamespace(String prefix) {
        return "it-" + prefix + '-' + UUID.randomUUID();
    }

    private static ServiceInstance instance(
            ServiceKey key,
            String id,
            int port) {
        return new ServiceInstance(
                id,
                key,
                new RpcEndpoint("127.0.0.1", port),
                100,
                Map.of("zone", "test"));
    }

    private static boolean await(
            Duration timeout,
            CheckedBoolean condition) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.get()) {
                return true;
            }
            Thread.sleep(100);
        }
        return condition.get();
    }

    @FunctionalInterface
    private interface CheckedBoolean {
        boolean get() throws Exception;
    }
}
