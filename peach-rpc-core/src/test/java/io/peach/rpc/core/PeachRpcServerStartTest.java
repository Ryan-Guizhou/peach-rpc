package io.peach.rpc.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.peach.rpc.api.RpcEndpoint;
import io.peach.rpc.api.ServiceInstance;
import io.peach.rpc.codec.RpcCodec;
import io.peach.rpc.codec.RpcCodecRegistry;
import io.peach.rpc.registry.ServiceRegistrar;
import io.peach.rpc.transport.RpcRequestHandler;
import io.peach.rpc.transport.RpcTransportServer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class PeachRpcServerStartTest {

    @Test
    void shouldRollbackRegistrationsWhenRegistryStartupFails() {
        FailingRegistry registry = new FailingRegistry();
        TestTransportServer transport = new TestTransportServer();
        RpcCodec codec = new NoopCodec();
        PeachRpcServer server = PeachRpcServer.builder()
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
