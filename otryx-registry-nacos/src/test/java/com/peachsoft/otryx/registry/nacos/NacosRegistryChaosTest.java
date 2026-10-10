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
 * <p>使用独立 Consumer、Provider A、Provider B Registry Client 模拟真实进程边界。
 * 该测试仅由 nacos-chaos Profile 执行，需要宿主机 Docker CLI。
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

        String group = unique("PEACH_RPC_CHAOS");
        Registry consumer = registry(
                endpoint,
                group,
                "DEFAULT");
        Registry providerA = registry(
                endpoint,
                group,
                "DEFAULT");
        Registry providerB = registry(
                endpoint,
                group,
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
                     consumer.subscribe(
                             key,
                             latest::set)) {
            providerA.registrar()
                    .orElseThrow()
                    .register(first)
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);

            assertTrue(await(
                    Duration.ofSeconds(30),
                    () -> lookupContains(
                            consumer,
                            key,
                            first)));
            assertTrue(await(
                    Duration.ofSeconds(30),
                    () -> containsIdentity(
                            latest.get(),
                            first)));

            docker("pause", container);
            paused = true;

            CompletableFuture<Void> duringPause =
                    providerB.registrar()
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

            if (!awaitFutureSuccess(
                    duringPause,
                    Duration.ofSeconds(15))) {
                assertTrue(await(
                        Duration.ofSeconds(30),
                        () -> registerEventually(
                                providerB,
                                second)));
            }

            assertTrue(await(
                    Duration.ofSeconds(30),
                    () -> {
                        RegistrySnapshot snapshot =
                                consumer.lookup(key)
                                        .toCompletableFuture()
                                        .get(
                                                5,
                                                TimeUnit.SECONDS);
                        return containsIdentity(
                                snapshot,
                                first)
                                && containsIdentity(
                                        snapshot,
                                        second);
                    }));
            assertTrue(await(
                    Duration.ofSeconds(30),
                    () -> containsIdentity(
                            latest.get(),
                            second)));
        } finally {
            if (paused) {
                docker("unpause", container);
            }
            providerA.close();
            providerB.close();
            consumer.close();
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
                new RpcEndpoint(
                        "127.0.0.1",
                        port),
                100,
                Map.of("zone", "chaos"));
    }

    private static boolean lookupContains(
            Registry registry,
            ServiceKey key,
            ServiceInstance instance) {
        try {
            return containsIdentity(
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
                    .get(
                            5,
                            TimeUnit.SECONDS);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean awaitFutureSuccess(
            CompletableFuture<Void> future,
            Duration timeout) {
        try {
            future.get(
                    timeout.toMillis(),
                    TimeUnit.MILLISECONDS);
            return true;
        } catch (Exception ignored) {
            return false;
        }
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
