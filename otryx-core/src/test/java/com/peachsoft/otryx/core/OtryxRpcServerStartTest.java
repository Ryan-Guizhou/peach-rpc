package com.peachsoft.otryx.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.peachsoft.otryx.api.OtryxRpcExecution;
import com.peachsoft.otryx.api.RpcEndpoint;
import com.peachsoft.otryx.api.RpcExecutionMode;
import com.peachsoft.otryx.api.ServiceInstance;
import com.peachsoft.otryx.codec.RpcCodec;
import com.peachsoft.otryx.codec.RpcCodecRegistry;
import com.peachsoft.otryx.registry.ServiceRegistrar;
import com.peachsoft.otryx.transport.RpcRequestHandler;
import com.peachsoft.otryx.transport.RpcTransportServer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * 验证 Provider 启动与服务绑定的基本生命周期。
 *
 * @Author Ryan
 * @Version 1.0.0-SNAPSHOT
 * @CreateTime 2026/9/23 10:51
 */
public class OtryxRpcServerStartTest {

    @Test
    void shouldRejectDirectExecutionUnlessExplicitlyEnabled() {
        ServiceRegistrar registry = new NoopRegistry();
        RpcTransportServer transport = new TestTransportServer();
        RpcCodecRegistry codecs =
                RpcCodecRegistry.of(new NoopCodec());

        OtryxRpcServer safeDefault = OtryxRpcServer.builder()
                .serviceRegistrar(registry)
                .transportServer(transport)
                .codecRegistry(codecs)
                .bindEndpoint(new RpcEndpoint("127.0.0.1", 19090))
                .build();
        try {
            assertThrows(
                    IllegalStateException.class,
                    () -> safeDefault.registerService(
                            DirectService.class,
                            new DirectServiceImpl(),
                            "1.0.0",
                            "default"));
        } finally {
            safeDefault.close();
        }

        OtryxRpcServer explicitlyEnabled = OtryxRpcServer.builder()
                .serviceRegistrar(registry)
                .transportServer(new TestTransportServer())
                .codecRegistry(codecs)
                .bindEndpoint(new RpcEndpoint("127.0.0.1", 19091))
                .executionOptions(new RpcProviderExecutionOptions(
                        true,
                        1,
                        16))
                .build();
        try {
            assertDoesNotThrow(() ->
                    explicitlyEnabled.registerService(
                            DirectService.class,
                            new DirectServiceImpl(),
                            "1.0.0",
                            "default"));
        } finally {
            explicitlyEnabled.close();
        }
    }

    @Test
    void closeShouldNotBlockForeverWhenRegistryUnregisterHangs() {
        HangingUnregisterRegistry registry =
                new HangingUnregisterRegistry();
        OtryxRpcServer server = OtryxRpcServer.builder()
                .serviceRegistrar(registry)
                .transportServer(new TestTransportServer())
                .codecRegistry(RpcCodecRegistry.of(new NoopCodec()))
                .bindEndpoint(new RpcEndpoint("127.0.0.1", 19092))
                .controlPlaneTimeout(Duration.ofMillis(50))
                .build()
                .registerService(
                        ServiceOne.class,
                        new ServiceOneImpl(),
                        "1.0.0",
                        "default");

        server.start().toCompletableFuture().join();
        long startedAt = System.nanoTime();
        server.close();
        long elapsedMillis =
                Duration.ofNanos(System.nanoTime() - startedAt)
                        .toMillis();

        org.junit.jupiter.api.Assertions.assertTrue(
                elapsedMillis < 1_000L);
    }

    @Test
    void wildcardBindShouldRequireAdvertisedHost() {
        OtryxRpcServer server = OtryxRpcServer.builder()
                .serviceRegistrar(new NoopRegistry())
                .transportServer(new TestTransportServer())
                .codecRegistry(RpcCodecRegistry.of(new NoopCodec()))
                .bindEndpoint(new RpcEndpoint("0.0.0.0", 19090))
                .build();

        try {
            CompletionException error = assertThrows(
                    CompletionException.class,
                    () -> server.start()
                            .toCompletableFuture()
                            .join());
            assertEquals(
                    "RPC advertised host is required when bind host is 0.0.0.0",
                    error.getCause().getMessage());
        } finally {
            server.close();
        }
    }

    @Test
    void dynamicPortShouldPublishActualTransportPort() {
        CapturingRegistry registry = new CapturingRegistry();
        OtryxRpcServer server = OtryxRpcServer.builder()
                .serviceRegistrar(registry)
                .transportServer(new DynamicPortTransportServer(24567))
                .codecRegistry(RpcCodecRegistry.of(new NoopCodec()))
                .bindEndpoint(new RpcEndpoint("127.0.0.1", 0))
                .build()
                .registerService(
                        ServiceOne.class,
                        new ServiceOneImpl(),
                        "1.0.0",
                        "default");

        try {
            server.start().toCompletableFuture().join();

            assertEquals(
                    new RpcEndpoint("127.0.0.1", 24567),
                    registry.registered.get().endpoint());
        } finally {
            server.close();
        }
    }

    @Test
    void shouldRollbackRegistrationsWhenRegistryStartupFails() {
        FailingRegistry registry = new FailingRegistry();
        TestTransportServer transport = new TestTransportServer();
        RpcCodec codec = new NoopCodec();
        OtryxRpcServer server = OtryxRpcServer.builder()
                .serviceRegistrar(registry)
                .transportServer(transport)
                .codecRegistry(RpcCodecRegistry.of(codec))
                .bindEndpoint(new RpcEndpoint("127.0.0.1", 19090))
                .build()
                .registerService(ServiceOne.class, new ServiceOneImpl(), "1.0.0", "default")
                .registerService(ServiceTwo.class, new ServiceTwoImpl(), "1.0.0", "default");

        try {
            assertThrows(
                    CompletionException.class,
                    () -> server.start().toCompletableFuture().join());
            assertEquals(2, registry.unregisterCount.get());
            assertEquals(1, transport.closeCount.get());
        } finally {
            server.close();
        }
    }

    public interface DirectService {

        @OtryxRpcExecution(RpcExecutionMode.DIRECT)
        String call();
    }

    public static final class DirectServiceImpl implements DirectService {
        @Override
        public String call() {
            return "direct";
        }
    }

    public interface ServiceOne {
        String call();
    }

    public interface ServiceTwo {
        String call();
    }

    public static final class ServiceOneImpl implements ServiceOne {
        @Override
        public String call() {
            return "one";
        }
    }

    public static final class ServiceTwoImpl implements ServiceTwo {
        @Override
        public String call() {
            return "two";
        }
    }

    private static final class NoopCodec implements RpcCodec {
        @Override
        public byte code() {
            return 1;
        }

        @Override
        public byte[] encode(Object value) {
            return new byte[0];
        }

        @Override
        public <T> T decode(byte[] bytes, Class<T> type) {
            throw new UnsupportedOperationException("Not used by this test");
        }
    }

    private static final class NoopRegistry implements ServiceRegistrar {
        @Override
        public CompletionStage<Void> register(ServiceInstance instance) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> unregister(ServiceInstance instance) {
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class HangingUnregisterRegistry
            implements ServiceRegistrar {

        @Override
        public CompletionStage<Void> register(ServiceInstance instance) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> unregister(ServiceInstance instance) {
            return new CompletableFuture<>();
        }
    }

    private static final class CapturingRegistry
            implements ServiceRegistrar {
        private final AtomicReference<ServiceInstance> registered =
                new AtomicReference<>();

        @Override
        public CompletionStage<Void> register(ServiceInstance instance) {
            registered.set(instance);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> unregister(ServiceInstance instance) {
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class DynamicPortTransportServer
            implements RpcTransportServer {
        private final int port;

        private DynamicPortTransportServer(int port) {
            this.port = port;
        }

        @Override
        public CompletionStage<RpcEndpoint> start(
                RpcEndpoint bind,
                RpcRequestHandler handler) {
            return CompletableFuture.completedFuture(
                    new RpcEndpoint(bind.host(), port));
        }

        @Override
        public void close() {
        }
    }

    private static final class FailingRegistry implements ServiceRegistrar {
        private final AtomicInteger registerCount = new AtomicInteger();
        private final AtomicInteger unregisterCount = new AtomicInteger();

        @Override
        public CompletionStage<Void> register(ServiceInstance instance) {
            if (registerCount.incrementAndGet() == 2) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("synthetic registry failure"));
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> unregister(ServiceInstance instance) {
            unregisterCount.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        }

    }

    private static final class TestTransportServer implements RpcTransportServer {
        private final AtomicInteger closeCount = new AtomicInteger();

        @Override
        public CompletionStage<RpcEndpoint> start(
                RpcEndpoint bind, RpcRequestHandler handler) {
            return CompletableFuture.completedFuture(bind);
        }

        @Override
        public void close() {
            closeCount.incrementAndGet();
        }
    }
}
