package io.peach.rpc.registry.etcd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.test.EtcdClusterExtension;
import io.peach.rpc.api.RpcEndpoint;
import io.peach.rpc.api.ServiceInstance;
import io.peach.rpc.api.ServiceKey;
import io.peach.rpc.registry.RegistryContractTestKit;
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
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

@Timeout(value = 90, unit = TimeUnit.SECONDS)
class EtcdRegistryIntegrationTest {

    @RegisterExtension
    static final EtcdClusterExtension CLUSTER =
            EtcdClusterExtension.builder()
                    .withClusterName("peach-rpc-etcd-it")
                    .withNodes(1)
                    .withSsl(false)
                    .build();

    @Test
    void shouldSatisfySharedRegistryContract() throws Exception {
        try (EtcdRegistry registry =
                     registry(
                             uniqueNamespace("contract"),
                             5)) {
            ServiceKey key = new ServiceKey(
                    "demo.Contract",
                    "1.0.0",
                    "contract");
            RegistryContractTestKit
                    .verifyRegistrationDiscoverySubscription(
                            registry,
                            key,
                            instance(
                                    key,
                                    "contract-a",
                                    19110),
                            instance(
                                    key,
                                    "contract-b",
                                    19111),
                            Duration.ofSeconds(15));
        }
    }

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
    void shouldRepublishActiveRegistrationAfterLeaseLoss()
            throws Exception {
        String namespace = uniqueNamespace("lease-recovery");
        ServiceKey key =
                new ServiceKey("demo.LeaseRecovery", "1.0.0", "default");
        ServiceInstance instance =
                instance(key, "node-recover", 19094);

        try (EtcdRegistry registry = registry(namespace, 2);
             Client rawClient = rawClient()) {
            registry.register(instance)
                    .toCompletableFuture()
                    .join();

            long originalLease = findLease(rawClient, instance.instanceId());
            assertTrue(originalLease > 0L);

            rawClient.getLeaseClient()
                    .revoke(originalLease)
                    .join();

            assertTrue(await(
                    Duration.ofSeconds(12),
                    () -> registry.lookup(key)
                            .toCompletableFuture()
                            .join()
                            .instances()
                            .contains(instance)));
            long recoveredLease =
                    findLease(rawClient, instance.instanceId());
            assertTrue(recoveredLease > 0L);
            assertTrue(recoveredLease != originalLease);
        }
    }

    @Test
    void shouldRecoverSubscriptionAfterCompaction()
            throws Exception {
        String namespace = uniqueNamespace("compaction");
        ServiceKey key =
                new ServiceKey("demo.Compaction", "1.0.0", "default");
        ServiceInstance first =
                instance(key, "node-compaction-a", 19100);
        ServiceInstance second =
                instance(key, "node-compaction-b", 19101);
        AtomicReference<RegistrySnapshot> latest =
                new AtomicReference<>();

        try (EtcdRegistry registry = registry(namespace, 5);
             Client client = rawClient()) {
            registry.register(first)
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
            long staleRevision = registry.lookup(key)
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS)
                    .revision();

            registry.register(second)
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
            long compactRevision = registry.lookup(key)
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS)
                    .revision();
            assertTrue(compactRevision > staleRevision);

            client.getKVClient()
                    .compact(compactRevision)
                    .get(10, TimeUnit.SECONDS);

            try (RegistrySubscription ignored =
                         registry.subscribeFromRevision(
                                 key,
                                 latest::set,
                                 staleRevision)) {
                assertTrue(await(
                        Duration.ofSeconds(15),
                        () -> contains(latest.get(), first)
                                && contains(latest.get(), second)));
                assertTrue(latest.get().revision() >= compactRevision);
            }
        }
    }

    @Test
    void shouldRecoverRegistrationAndSubscriptionAfterClusterRestart()
            throws Exception {
        String namespace = uniqueNamespace("cluster-restart");
        ServiceKey key =
                new ServiceKey("demo.ClusterRestart", "1.0.0", "default");
        ServiceInstance first =
                instance(key, "node-restart-a", 19095);
        ServiceInstance second =
                instance(key, "node-restart-b", 19096);
        AtomicReference<RegistrySnapshot> latest =
                new AtomicReference<>();

        try (EtcdRegistry registry =
                     restartAwareRegistry(namespace, 2);
             RegistrySubscription ignored =
                     registry.subscribe(key, latest::set)) {
            registry.register(first)
                    .toCompletableFuture()
                    .join();

            assertTrue(await(
                    Duration.ofSeconds(10),
                    () -> contains(latest.get(), first)));

            CLUSTER.restart(2, TimeUnit.SECONDS);

            assertTrue(await(
                    Duration.ofSeconds(20),
                    () -> lookupContains(
                            registry,
                            key,
                            first)));

            assertTrue(await(
                    Duration.ofSeconds(20),
                    () -> registerEventually(
                            registry,
                            second)));

            assertTrue(await(
                    Duration.ofSeconds(20),
                    () -> contains(
                            latest.get(),
                            first)
                            && contains(
                                    latest.get(),
                                    second)));
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

    private static EtcdRegistry restartAwareRegistry(
            String namespace,
            long leaseTtlSeconds) {
        Client client = Client.builder()
                .target("cluster://" + CLUSTER.clusterName())
                .build();
        return new EtcdRegistry(
                client,
                leaseTtlSeconds,
                namespace);
    }

    private static EtcdRegistry registry(
            String namespace,
            long leaseTtlSeconds) {
        return new EtcdRegistry(
                endpoints(),
                leaseTtlSeconds,
                namespace);
    }

    private static Client rawClient() {
        return Client.builder().endpoints(endpoints()).build();
    }

    private static String[] endpoints() {
        return CLUSTER.clientEndpoints().stream()
                .map(URI::toString)
                .toArray(String[]::new);
    }

    private static long findLease(
            Client client,
            String instanceId) {
        var response = client.getKVClient()
                .get(
                        ByteSequence.from(
                                "/peach-rpc/",
                                java.nio.charset.StandardCharsets.UTF_8),
                        GetOption.builder().isPrefix(true).build())
                .join();
        return response.getKvs().stream()
                .filter(kv -> kv.getValue()
                        .toString(java.nio.charset.StandardCharsets.UTF_8)
                        .startsWith(instanceId + "|"))
                .mapToLong(kv -> kv.getLease())
                .findFirst()
                .orElse(0L);
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

    private static boolean lookupContains(
            EtcdRegistry registry,
            ServiceKey key,
            ServiceInstance instance) {
        try {
            return registry.lookup(key)
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS)
                    .instances()
                    .contains(instance);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean registerEventually(
            EtcdRegistry registry,
            ServiceInstance instance) {
        try {
            registry.register(instance)
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean contains(
            RegistrySnapshot snapshot,
            ServiceInstance instance) {
        return snapshot != null
                && snapshot.instances().contains(instance);
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
