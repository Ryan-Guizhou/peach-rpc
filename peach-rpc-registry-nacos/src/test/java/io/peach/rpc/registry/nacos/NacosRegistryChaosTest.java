package io.peach.rpc.registry.nacos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.peach.rpc.api.RpcEndpoint;
import io.peach.rpc.api.ServiceInstance;
import io.peach.rpc.api.ServiceKey;
import io.peach.rpc.registry.Registry;
import io.peach.rpc.registry.RegistryOptions;
import io.peach.rpc.registry.RegistrySnapshot;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Nacos 容器暂停/恢复故障注入测试。
 *
 * <p>该测试仅由 nacos-chaos Profile 执行，需要宿主机 Docker CLI。
 */
@Tag("chaos")
@Timeout(value = 150, unit = TimeUnit.SECONDS)
class NacosRegistryChaosTest {

    @Test
    void shouldRecoverRegistrationAndSubscriptionAfterNetworkPause()
            throws Exception {
        String endpoint = System.getenv("NACOS_TEST_ENDPOINT");
        String container =
                System.getenv("NACOS_CHAOS_CONTAINER");
        assumeTrue(
                endpoint != null && !endpoint.isBlank(),
                "NACOS_TEST_ENDPOINT is required");
        assumeTrue(
                container != null && !container.isBlank(),
                "NACOS_CHAOS_CONTAINER is required");

        Registry registry = registry(
                endpoint,
                unique("PEACH_RPC_CHAOS"),
                "DEFAULT");
        ServiceKey key = new ServiceKey(
                "demo.NacosChaos",
                "1.0.0",
                "chaos");
        ServiceInstance first =
                instance(key, "node-a", 19120);
        ServiceInstance second =
                instance(key, "node-b", 19121);
        AtomicReference<RegistrySnapshot> latest =
                new AtomicReference<>();
        boolean paused = false;

        try (var subscription =
                     registry.subscribe(
                             key,
                             latest::set)) {
            assertTrue(await(
                    Duration.ofSeconds(30),
                    () -> registerAndLookupEventually(
                            registry,
                            key,
                            first)));

            docker("pause", container);
            paused = true;

            CompletableFuture<Void> duringPause =
                    registry.registrar()
                            .orElseThrow()
                            .register(second)
                            .toCompletableFuture();
            Thread.sleep(1500L);
            assertFalse(
                    duringPause.isDone()
                            && !duringPause.isCompletedExceptionally(),
                    "Registration must not succeed while Nacos is paused");

            docker("unpause", container);
            paused = false;

            assertTrue(await(
                    Duration.ofSeconds(30),
                    () -> registerEventually(
                            registry,
                            second)));
            assertTrue(await(
                    Duration.ofSeconds(30),
                    () -> {
                        RegistrySnapshot snapshot =
                                registry.lookup(key)
                                        .toCompletableFuture()
                                        .get(5, TimeUnit.SECONDS);
                        return contains(snapshot, first)
                                && contains(snapshot, second);
                    }));
            assertTrue(await(
                    Duration.ofSeconds(30),
                    () -> contains(latest.get(), second)));
        } finally {
            if (paused) {
                docker("unpause", container);
            }
            registry.close();
        }
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
            String id,
            int port) {
        return new ServiceInstance(
                id,
                key,
                new RpcEndpoint("127.0.0.1", port),
                100,
                Map.of("zone", "chaos"));
    }

    private static boolean registerAndLookupEventually(
            Registry registry,
            ServiceKey key,
            ServiceInstance instance) {
        if (!registerEventually(
                registry,
                instance)) {
            return false;
        }
        try {
            return contains(
                    registry.lookup(key)
                            .toCompletableFuture()
                            .get(
                                    5,
                                    TimeUnit.SECONDS),
                    instance);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean registerEventually(
            Registry registry,
            ServiceInstance instance) {
        try {
            registry.registrar()
                    .orElseThrow()
                    .register(instance)
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

    private static void docker(
            String action,
            String container)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
                "docker",
                action,
                container)
                .redirectErrorStream(true)
                .start();
        assertEquals(
                0,
                process.waitFor(),
                "docker " + action + " failed");
    }

    private static boolean await(
            Duration timeout,
            CheckedBoolean condition)
            throws Exception {
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

    private static String unique(String prefix) {
        return prefix
                + '_'
                + UUID.randomUUID()
                        .toString()
                        .replace("-", "");
    }

    @FunctionalInterface
    private interface CheckedBoolean {
        boolean get() throws Exception;
    }
}
