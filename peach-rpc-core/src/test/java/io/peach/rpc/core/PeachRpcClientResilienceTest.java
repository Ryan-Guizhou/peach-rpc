package io.peach.rpc.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.peach.rpc.api.PeachRpcIdempotent;
import io.peach.rpc.api.RpcEndpoint;
import io.peach.rpc.api.RpcMethodDescriptor;
import io.peach.rpc.api.RpcStatus;
import io.peach.rpc.api.RpcUnavailableException;
import io.peach.rpc.api.ServiceInstance;
import io.peach.rpc.api.ServiceKey;
import io.peach.rpc.codec.RpcCodec;
import io.peach.rpc.codec.RpcCodecRegistry;
import io.peach.rpc.codec.RpcMethodCodec;
import io.peach.rpc.protocol.RpcFrameView;
import io.peach.rpc.protocol.RpcProtocolCodec;
import io.peach.rpc.registry.RegistryListener;
import io.peach.rpc.registry.RegistrySnapshot;
import io.peach.rpc.registry.RegistrySubscription;
import io.peach.rpc.registry.ServiceDiscovery;
import io.peach.rpc.transport.RpcTransportClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PeachRpcClientResilienceTest {

    @Test
    void shouldRetryOnlyIdempotentMethodWithinBudget() {
        AtomicInteger attempts = new AtomicInteger();
        try (PeachRpcClient client = client((endpoint, frame, timeout) -> {
            if (attempts.incrementAndGet() == 1) {
                return CompletableFuture.failedFuture(
                        new RpcUnavailableException("temporary"));
            }
            return CompletableFuture.completedFuture(
                    successResponse(frame, "ok"));
        })) {
            RetryService service = client.refer(
                    RetryService.class,
                    "1.0.0",
                    "test");

            assertEquals("ok", service.find("42"));
            assertEquals(2, attempts.get());
        }
    }

    @Test
    void shouldNotRetryMethodWithoutIdempotentContract() {
        AtomicInteger attempts = new AtomicInteger();
        try (PeachRpcClient client = client((endpoint, frame, timeout) -> {
            attempts.incrementAndGet();
            return CompletableFuture.failedFuture(
                    new RpcUnavailableException("temporary"));
        })) {
            RetryService service = client.refer(
                    RetryService.class,
                    "1.0.0",
                    "test");

            CompletionException error = assertThrows(
                    CompletionException.class,
                    () -> service.create("42"));
            assertInstanceOf(
                    RpcUnavailableException.class,
                    error.getCause());
            assertEquals(1, attempts.get());
        }
    }

    private static PeachRpcClient client(RequestFunction request) {
        RpcClientResilienceOptions resilience =
                new RpcClientResilienceOptions(
                        2,
                        0.0d,
                        1,
                        1,
                        Duration.ZERO,
                        Duration.ZERO,
                        100,
                        Duration.ofSeconds(1),
                        100,
                        Duration.ofSeconds(1));
        return PeachRpcClient.builder()
                .serviceDiscovery(new StaticDiscovery())
                .transportClient(new TestTransport(request))
                .codecRegistry(RpcCodecRegistry.of(new StringCodec()))
                .timeout(Duration.ofSeconds(1))
                .resilienceOptions(resilience)
                .build();
    }

    private static byte[] successResponse(
            byte[] requestBytes,
            String value) {
        RpcFrameView request = RpcProtocolCodec.view(requestBytes);
        return RpcProtocolCodec.encodeResponse(
                (byte) 1,
                RpcStatus.OK,
                request.requestId(),
                request.serviceId(),
                request.methodId(),
                value.getBytes(StandardCharsets.UTF_8));
    }

    interface RetryService {

        @PeachRpcIdempotent
        String find(String value);

        String create(String value);
    }

    @FunctionalInterface
    private interface RequestFunction {

        CompletionStage<byte[]> request(
                RpcEndpoint endpoint,
                byte[] frame,
                Duration timeout);
    }

    private static final class TestTransport
            implements RpcTransportClient {

        private final RequestFunction request;

        private TestTransport(RequestFunction request) {
            this.request = request;
        }

        @Override
        public CompletionStage<byte[]> request(
                RpcEndpoint endpoint,
                byte[] frame,
                Duration timeout) {
            return request.request(endpoint, frame, timeout);
        }

        @Override
        public void close() {
        }
    }

    private static final class StaticDiscovery
            implements ServiceDiscovery {

        @Override
        public CompletionStage<RegistrySnapshot> lookup(ServiceKey key) {
            return CompletableFuture.completedFuture(snapshot(key));
        }

        @Override
        public RegistrySubscription subscribe(
                ServiceKey key,
                RegistryListener listener) {
            listener.onSnapshot(snapshot(key));
            return () -> { };
        }

        private static RegistrySnapshot snapshot(ServiceKey key) {
            return new RegistrySnapshot(
                    List.of(new ServiceInstance(
                            "node-1",
                            key,
                            new RpcEndpoint("127.0.0.1", 19090),
                            100,
                            Map.of())),
                    1L);
        }
    }

    private static final class StringCodec
            implements RpcCodec {

        @Override
        public byte code() {
            return 1;
        }

        @Override
        public byte[] encode(Object value) {
            return value == null
                    ? new byte[0]
                    : value.toString().getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public <T> T decode(byte[] bytes, Class<T> type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RpcMethodCodec bind(RpcMethodDescriptor descriptor) {
            return new RpcMethodCodec() {
                @Override
                public byte codecId() {
                    return 1;
                }

                @Override
                public byte[] encodeArguments(Object[] arguments) {
                    return arguments.length == 0
                            ? new byte[0]
                            : arguments[0].toString()
                                    .getBytes(StandardCharsets.UTF_8);
                }

                @Override
                public Object[] decodeArguments(byte[] payload) {
                    return new Object[] {
                            new String(payload, StandardCharsets.UTF_8)
                    };
                }

                @Override
                public byte[] encodeResult(Object value) {
                    return encode(value);
                }

                @Override
                public Object decodeResult(byte[] payload) {
                    return new String(
                            payload,
                            StandardCharsets.UTF_8);
                }
            };
        }
    }
}
