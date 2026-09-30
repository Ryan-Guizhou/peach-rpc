package io.peach.rpc.registry;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.peach.rpc.api.ServiceInstance;
import io.peach.rpc.api.ServiceKey;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Registry Adapter 共享行为契约。
 *
 * <p>真实 Adapter 测试负责创建后端和 Registry，本 TestKit 统一验证
 * register/discovery/subscription/unregister 的基础可观察语义。
 */
public final class RegistryContractTestKit {

    private RegistryContractTestKit() {
    }

    /**
     * 验证支持注册与订阅的 Registry 基本契约。
     *
     * @param registry 待验证 Registry
     * @param key 服务键
     * @param instance 服务实例
     * @param timeout 最终一致等待上限
     * @throws Exception 等待或控制面调用失败
     */
    public static void verifyRegistrationDiscoverySubscription(
            Registry registry,
            ServiceKey key,
            ServiceInstance instance,
            Duration timeout) throws Exception {
        assertTrue(registry.capabilities().supports(
                RegistryCapability.REGISTRATION));
        assertTrue(registry.capabilities().supports(
                RegistryCapability.SUBSCRIPTION));

        ServiceRegistrar registrar =
                registry.registrar().orElseThrow();
        java.util.concurrent.atomic.AtomicReference<RegistrySnapshot> latest =
                new java.util.concurrent.atomic.AtomicReference<>();

        try (RegistrySubscription ignored =
                     registry.subscribe(key, latest::set)) {
            registrar.register(instance)
                    .toCompletableFuture()
                    .get(
                            timeout.toSeconds(),
                            TimeUnit.SECONDS);

            assertTrue(await(
                    timeout,
                    () -> lookupIds(registry, key)
                            .contains(instance.instanceId())));
            assertTrue(await(
                    timeout,
                    () -> snapshotIds(latest.get())
                            .contains(instance.instanceId())));

            registrar.unregister(instance)
                    .toCompletableFuture()
                    .get(
                            timeout.toSeconds(),
                            TimeUnit.SECONDS);

            assertTrue(await(
                    timeout,
                    () -> lookupIds(registry, key)
                            .isEmpty()));
            assertTrue(await(
                    timeout,
                    () -> snapshotIds(latest.get())
                            .isEmpty()));
        }
    }

    private static Set<String> lookupIds(
            Registry registry,
            ServiceKey key) throws Exception {
        RegistrySnapshot snapshot = registry.lookup(key)
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
        return snapshotIds(snapshot);
    }

    private static Set<String> snapshotIds(
            RegistrySnapshot snapshot) {
        if (snapshot == null) {
            return Set.of();
        }
        return snapshot.instances().stream()
                .map(ServiceInstance::instanceId)
                .collect(Collectors.toUnmodifiableSet());
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
