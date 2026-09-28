package io.peach.rpc.registry.nacos;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.peach.rpc.api.RpcEndpoint;
import io.peach.rpc.api.ServiceInstance;
import io.peach.rpc.api.ServiceKey;
import io.peach.rpc.registry.Registry;
import io.peach.rpc.registry.RegistryOptions;
import io.peach.rpc.registry.RegistrySnapshot;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

/** 真实 Nacos 服务发现集成测试。 */
class NacosRegistryIntegrationTest {

    @Test
    void shouldRegisterSubscribeLookupAndUnregister()
            throws Exception {
        String endpoint = System.getenv(
                "NACOS_TEST_ENDPOINT");
        assumeTrue(
                endpoint != null && !endpoint.isBlank(),
                "NACOS_TEST_ENDPOINT is required");

        Registry registry = new NacosRegistryFactory().create(
                new RegistryOptions(
                        List.of(endpoint),
                        "public",
                        Map.of(
                                "nacosGroup",
                                "PEACH_RPC_IT",
                                "nacosCluster",
                                "DEFAULT")));
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
                100,
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
                    () -> registry.lookup(key)
                            .toCompletableFuture()
                            .join()
                            .instances()
                            .stream()
                            .anyMatch(value ->
                                    value.instanceId()
                                            .equals("node-it"))));
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
