package com.peachsoft.otryx.registry.nacos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.peachsoft.otryx.api.RpcEndpoint;
import com.peachsoft.otryx.api.ServiceInstance;
import com.peachsoft.otryx.api.ServiceKey;
import com.peachsoft.otryx.registry.Registry;
import com.peachsoft.otryx.registry.RegistryContractTestKit;
import com.peachsoft.otryx.registry.RegistryOptions;
import com.peachsoft.otryx.registry.RegistrySnapshot;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** 真实 Nacos 服务发现集成测试。 */
class NacosRegistryIntegrationTest {

    @Test
    void shouldSatisfySharedRegistryContract()
            throws Exception {
        Registry registry = registry(
                endpoint(),
                unique("OTRYX_RPC_CONTRACT"),
                "DEFAULT");
        ServiceKey key = new ServiceKey(
                "demo.NacosContract",
                "1.0.0",
                "contract");

        try {
            RegistryContractTestKit
                    .verifyRegistrationDiscoverySubscription(
                            registry,
                            key,
                            instance(
                                    key,
                                    "contract-a",
                                    19112),
                            Duration.ofSeconds(20));
        } finally {
            registry.close();
        }
    }

    @Test
    void shouldRegisterSubscribeLookupAndUnregister()
            throws Exception {
        String endpoint = endpoint();
        String group = unique("OTRYX_RPC_IT");
        Registry registry = registry(
                endpoint,
                group,
                "DEFAULT");
        ServiceKey key = new ServiceKey(
                "demo.NacosIntegration",
                "1.0.0",
                "it");
        ServiceInstance instance = new ServiceInstance(
                "node-it",
                key,
                new RpcEndpoint(
                        "127.0.0.1",
                        19090),
                50,
                Map.of("zone", "test"));
        CopyOnWriteArrayList<RegistrySnapshot> snapshots =
                new CopyOnWriteArrayList<>();

        try (var subscription =
                     registry.subscribe(
                             key,
                             snapshots::add)) {
            registry.registrar()
                    .orElseThrow()
                    .register(instance)
                    .toCompletableFuture()
                    .join();

            assertTrue(await(
                    Duration.ofSeconds(15),
                    () -> {
                        List<ServiceInstance> instances =
                                registry.lookup(key)
                                        .toCompletableFuture()
                                        .join()
                                        .instances();
                        if (instances.size() != 1) {
                            return false;
                        }
                        ServiceInstance discovered =
                                instances.getFirst();
                        return discovered.instanceId()
                                        .equals("node-it")
                                && discovered.weight() == 50
                                && "test".equals(
                                        discovered.metadata()
                                                .get("zone"));
                    }));
            assertTrue(await(
                    Duration.ofSeconds(15),
                    () -> snapshots.stream()
                            .anyMatch(snapshot ->
                                    snapshot.instances()
                                            .stream()
                                            .anyMatch(value ->
                                                    value.instanceId()
                                                            .equals(
                                                                    "node-it")))));

            registry.registrar()
                    .orElseThrow()
                    .unregister(instance)
                    .toCompletableFuture()
                    .join();
            assertTrue(await(
                    Duration.ofSeconds(15),
                    () -> registry.lookup(key)
                            .toCompletableFuture()
                            .join()
                            .instances()
                            .isEmpty()));
        } finally {
            registry.close();
        }
    }

    @Test
    void remoteProviderRemovalShouldPublishEmptySnapshot()
            throws Exception {
        String endpoint = endpoint();
        String group = unique("OTRYX_RPC_REMOTE");
        Registry provider = registry(
                endpoint,
                group,
                "DEFAULT");
        Registry consumer = registry(
                endpoint,
                group,
                "DEFAULT");
        ServiceKey key = new ServiceKey(
                "demo.NacosRemoteLifecycle",
                "1.0.0",
                "remote");
        ServiceInstance instance = instance(
                key,
                "remote-node",
                19094);
        AtomicReference<RegistrySnapshot> latest =
                new AtomicReference<>();

        try (var subscription =
                     consumer.subscribe(
                             key,
                             latest::set)) {
            provider.registrar()
                    .orElseThrow()
                    .register(instance)
                    .toCompletableFuture()
                    .get(10, java.util.concurrent.TimeUnit.SECONDS);

            assertTrue(await(
                    Duration.ofSeconds(20),
                    () -> containsIdentity(
                            latest.get(),
                            instance)));

            provider.registrar()
                    .orElseThrow()
                    .unregister(instance)
                    .toCompletableFuture()
                    .get(10, java.util.concurrent.TimeUnit.SECONDS);

            assertTrue(await(
                    Duration.ofSeconds(20),
                    () -> latest.get() != null
                            && latest.get()
                                    .instances()
                                    .isEmpty()));
        } finally {
            provider.close();
            consumer.close();
        }
    }

    @Test
    void shouldIsolateNacosGroups() throws Exception {
        String endpoint = endpoint();
        Registry left = registry(
                endpoint,
                unique("OTRYX_RPC_GROUP_A"),
                "DEFAULT");
        Registry right = registry(
                endpoint,
                unique("OTRYX_RPC_GROUP_B"),
                "DEFAULT");
        ServiceKey key = new ServiceKey(
                "demo.GroupIsolation",
                "1.0.0",
                "default");
        ServiceInstance instance = instance(
                key,
                "group-node",
                19091);

        try {
            left.registrar()
                    .orElseThrow()
                    .register(instance)
                    .toCompletableFuture()
                    .join();

            assertTrue(await(
                    Duration.ofSeconds(10),
                    () -> !left.lookup(key)
                            .toCompletableFuture()
                            .join()
                            .instances()
                            .isEmpty()));
            assertTrue(
                    right.lookup(key)
                            .toCompletableFuture()
                            .join()
                            .instances()
                            .isEmpty());
        } finally {
            left.close();
            right.close();
        }
    }

    @Test
    void shouldIsolateClusters() throws Exception {
        String endpoint = endpoint();
        String group = unique("OTRYX_RPC_CLUSTER");
        Registry left = registry(
                endpoint,
                group,
                "CLUSTER-A");
        Registry right = registry(
                endpoint,
                group,
                "CLUSTER-B");
        ServiceKey key = new ServiceKey(
                "demo.ClusterIsolation",
                "1.0.0",
                "default");
        ServiceInstance instance = instance(
                key,
                "cluster-node",
                19092);

        try {
            left.registrar()
                    .orElseThrow()
                    .register(instance)
                    .toCompletableFuture()
                    .join();

            assertTrue(await(
                    Duration.ofSeconds(10),
                    () -> !left.lookup(key)
                            .toCompletableFuture()
                            .join()
                            .instances()
                            .isEmpty()));
            assertTrue(
                    right.lookup(key)
                            .toCompletableFuture()
                            .join()
                            .instances()
                            .isEmpty());
        } finally {
            left.close();
            right.close();
        }
    }

    @Test
    void shouldIsolateServiceVersionAndRpcGroup()
            throws Exception {
        String endpoint = endpoint();
        Registry registry = registry(
                endpoint,
                unique("OTRYX_RPC_KEY"),
                "DEFAULT");
        ServiceKey registeredKey = new ServiceKey(
                "demo.KeyIsolation",
                "1.0.0",
                "blue");
        ServiceKey otherVersion = new ServiceKey(
                "demo.KeyIsolation",
                "2.0.0",
                "blue");
        ServiceKey otherGroup = new ServiceKey(
                "demo.KeyIsolation",
                "1.0.0",
                "green");
        ServiceInstance instance = instance(
                registeredKey,
                "key-node",
                19093);

        try {
            registry.registrar()
                    .orElseThrow()
                    .register(instance)
                    .toCompletableFuture()
                    .join();

            assertTrue(await(
                    Duration.ofSeconds(10),
                    () -> !registry.lookup(registeredKey)
                            .toCompletableFuture()
                            .join()
                            .instances()
                            .isEmpty()));
            assertTrue(
                    registry.lookup(otherVersion)
                            .toCompletableFuture()
                            .join()
                            .instances()
                            .isEmpty());
            assertTrue(
                    registry.lookup(otherGroup)
                            .toCompletableFuture()
                            .join()
                            .instances()
                            .isEmpty());
        } finally {
            registry.close();
        }
    }

    @Test
    void closeShouldBeIdempotent() {
        Registry registry = registry(
                endpoint(),
                unique("OTRYX_RPC_CLOSE"),
                "DEFAULT");

        registry.close();
        registry.close();
    }

    private static Registry registry(
            String endpoint,
            String group,
            String cluster) {
        return new NacosRegistryFactory().create(
                new RegistryOptions(
                        List.of(endpoint),
                        "public",
                        Map.of(
                                "nacosGroup",
                                group,
                                "nacosCluster",
                                cluster)));
    }

    private static ServiceInstance instance(
            ServiceKey key,
            String instanceId,
            int port) {
        return new ServiceInstance(
                instanceId,
                key,
                new RpcEndpoint(
                        "127.0.0.1",
                        port),
                100,
                Map.of("zone", "test"));
    }

    private static boolean containsIdentity(
            RegistrySnapshot snapshot,
            ServiceInstance expected) {
        return snapshot != null
                && snapshot.instances()
                        .stream()
                        .anyMatch(value ->
                                value.instanceId()
                                        .equals(
                                                expected.instanceId())
                                        && value.endpoint()
                                                .equals(
                                                        expected.endpoint()));
    }

    private static String endpoint() {
        String endpoint = System.getenv(
                "NACOS_TEST_ENDPOINT");
        assumeTrue(
                endpoint != null && !endpoint.isBlank(),
                "NACOS_TEST_ENDPOINT is required");
        return endpoint;
    }

    private static String unique(String prefix) {
        return prefix
                + '_'
                + UUID.randomUUID()
                        .toString()
                        .replace("-", "");
    }

    private static boolean await(
            Duration timeout,
            CheckedBoolean condition) throws Exception {
        long deadline =
                System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.get()) {
                return true;
            }
            Thread.sleep(100L);
        }
        return condition.get();
    }

    @FunctionalInterface
    private interface CheckedBoolean {
        boolean get() throws Exception;
    }
}
