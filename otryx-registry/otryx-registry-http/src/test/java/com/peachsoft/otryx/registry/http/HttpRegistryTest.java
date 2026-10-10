package com.peachsoft.otryx.registry.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.peachsoft.otryx.api.RpcEndpoint;
import com.peachsoft.otryx.api.ServiceInstance;
import com.peachsoft.otryx.api.ServiceKey;
import com.peachsoft.otryx.observability.RpcObserver;
import com.peachsoft.otryx.registry.RegistryContractTestKit;
import com.peachsoft.otryx.registry.RegistrySnapshot;
import com.peachsoft.otryx.registry.RegistrySubscription;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * HTTP 控制面共用异步注册、轮询、故障与关闭行为。
 *
 * @Author Ryan
 * @Version 1.0.0
 * @CreateTime 2026/10/10 18:00
 */
class HttpRegistryTest {

    @Test
    void satisfiesSharedRegistryContractAndRenews() throws Exception {
        FakeBackend backend = new FakeBackend();
        ServiceKey key = new ServiceKey("demo.Api", "1", "a");
        ServiceInstance instance = instance(key);
        try (HttpRegistry registry = new HttpRegistry(
                backend, Duration.ofMillis(200),
                Duration.ofMillis(250), RpcObserver.noop())) {
            RegistryContractTestKit.verifyRegistrationDiscoverySubscription(
                    registry, key, instance, Duration.ofSeconds(8));
            registry.register(instance).toCompletableFuture().get(5, TimeUnit.SECONDS);
            Thread.sleep(650L);
            assertTrue(backend.renews.get() > 0);
            assertTrue(backend.threadName.get().startsWith("otryx-test-control-"));
        }
        assertTrue(backend.closed);
    }

    @Test
    void transientDiscoveryFailureDoesNotReplaceLastSnapshot() throws Exception {
        FakeBackend backend = new FakeBackend();
        ServiceKey key = new ServiceKey("demo.Api", "1", "a");
        AtomicReference<RegistrySnapshot> snapshot = new AtomicReference<>();
        try (HttpRegistry registry = new HttpRegistry(
                backend, Duration.ofMillis(200),
                Duration.ofSeconds(2), RpcObserver.noop())) {
            registry.register(instance(key)).toCompletableFuture().get(5, TimeUnit.SECONDS);
            try (RegistrySubscription ignored = registry.subscribe(key, snapshot::set)) {
                await(() -> snapshot.get() != null
                        && snapshot.get().instances().size() == 1);
                long revision = snapshot.get().revision();
                backend.failLookup = true;
                Thread.sleep(500L);
                assertEquals(revision, snapshot.get().revision());
                backend.failLookup = false;
                registry.unregister(instance(key)).toCompletableFuture().get(5, TimeUnit.SECONDS);
                await(() -> snapshot.get().instances().isEmpty());
                assertTrue(snapshot.get().revision() > revision);
            }
        }
    }

    @Test
    void closedRegistryMustRejectNewOperations() {
        HttpRegistry registry = new HttpRegistry(
                new FakeBackend(), Duration.ofMillis(200),
                Duration.ofSeconds(1), RpcObserver.noop());
        registry.close();
        registry.close();
        assertThrows(IllegalStateException.class,
                () -> registry.lookup(new ServiceKey("demo.Api", "1", "a")));
    }

    private static ServiceInstance instance(ServiceKey key) {
        return new ServiceInstance(
                "node-1", key, new RpcEndpoint("127.0.0.1", 19090),
                100, java.util.Map.of("peach.rpc.protocol.version", "1"));
    }

    private static void await(java.util.function.BooleanSupplier check) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(6);
        while (System.nanoTime() < deadline) {
            if (check.getAsBoolean()) {
                return;
            }
            Thread.sleep(50L);
        }
        assertTrue(check.getAsBoolean());
    }

    private static final class FakeBackend implements HttpRegistryBackend {
        private final ConcurrentHashMap<String, ServiceInstance> map =
                new ConcurrentHashMap<>();
        private final AtomicInteger renews = new AtomicInteger();
        private final AtomicReference<String> threadName = new AtomicReference<>();
        private volatile boolean failLookup;
        private volatile boolean closed;

        @Override
        public String type() {
            return "test";
        }

        @Override
        public void register(ServiceInstance instance) {
            threadName.set(Thread.currentThread().getName());
            map.put(instance.instanceId(), instance);
        }

        @Override
        public void renew(ServiceInstance instance) {
            renews.incrementAndGet();
        }

        @Override
        public void unregister(ServiceInstance instance) {
            map.remove(instance.instanceId());
        }

        @Override
        public List<ServiceInstance> lookup(ServiceKey key) throws IOException {
            if (failLookup) {
                throw new IOException("test registry outage");
            }
            return map.values().stream()
                    .filter(instance -> key.equals(instance.serviceKey())).toList();
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
