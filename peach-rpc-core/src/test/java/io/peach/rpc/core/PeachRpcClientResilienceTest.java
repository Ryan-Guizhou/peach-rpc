package io.peach.rpc.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import io.peach.rpc.observability.RpcObserver;
import io.peach.rpc.observability.RpcRetryExhaustionReason;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
    void shouldObserveRetryExhaustionAtMaxAttempts() {
        AtomicInteger attempts =
                new AtomicInteger();
        AtomicReference<RpcRetryExhaustionReason> reason =
                new AtomicReference<>();
        RpcObserver observer = new RpcObserver() {
            @Override
            public void onClientRetryExhausted(
                    ServiceKey serviceKey,
                    int methodId,
                    RpcRetryExhaustionReason exhaustionReason,
                    Throwable cause) {
                reason.set(exhaustionReason);
            }
        };

        try (PeachRpcClient client = client(
                (endpoint, frame, timeout) -> {
                    attempts.incrementAndGet();
                    return CompletableFuture.failedFuture(
                            new RpcUnavailableException(
                                    "temporary"));
                },
                observer)) {
            RetryService service = client.refer(
                    RetryService.class,
                    "1.0.0",
                    "test");

            CompletionException error = assertThrows(
                    CompletionException.class,
                    () -> service.find("42"));

            assertInstanceOf(
                    RpcUnavailableException.class,
                    error.getCause());
            assertEquals(
                    2,
                    attempts.get());
            assertEquals(
                    RpcRetryExhaustionReason.MAX_ATTEMPTS,
                    reason.get());
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

    @Test
    void singleFailingObserverMustNotBreakRpcSuccessOrInflightBalance() {
        AtomicInteger attemptCount = new AtomicInteger();
        AtomicInteger inflightBalance = new AtomicInteger();
        RpcObserver failingObserver = new RpcObserver() {
            @Override
            public void onClientInflightChanged(int delta) {
                inflightBalance.addAndGet(delta);
                throw new IllegalStateException("Metrics backend unavailable");
            }

            @Override
            public void onClientAttemptCompleted(
                    ServiceKey serviceKey,
                    int methodId,
                    RpcEndpoint endpoint,
                    int attempt,
                    long durationNanos,
                    RpcStatus status,
                    Throwable error) {
                throw new IllegalStateException("Attempt metrics failed");
            }

            @Override
            public void onClientCallCompleted(
                    ServiceKey serviceKey,
                    int methodId,
                    long durationNanos,
                    RpcStatus status,
                    Throwable error) {
                throw new IllegalStateException("Completion metrics failed");
            }
        };

        try (PeachRpcClient client = client(
                (endpoint, frame, timeout) -> {
                    attemptCount.incrementAndGet();
                    return CompletableFuture.completedFuture(
                            successResponse(frame, "ok"));
                },
                failingObserver)) {
            RetryService service = client.refer(
                    RetryService.class, "1.0.0", "test");
            assertEquals("ok", service.find("one"));
            assertEquals("ok", service.create("two"));
            assertEquals(2, attemptCount.get());
            assertEquals(0, inflightBalance.get());
        }
    }

    @Test
    void failingObserverMustNotSuppressRetryOrOriginalFailure() {
        AtomicInteger attempts = new AtomicInteger();
        RpcObserver failingObserver = new RpcObserver() {
            @Override
            public void onClientRetryScheduled(
                    ServiceKey serviceKey,
                    int methodId,
                    int nextAttempt,
                    long delayMillis,
                    Throwable cause) {
                throw new IllegalStateException("Retry metrics failed");
            }

            @Override
            public void onClientRetryExhausted(
                    ServiceKey serviceKey,
                    int methodId,
                    RpcRetryExhaustionReason reason,
                    Throwable cause) {
                throw new IllegalStateException("Exhaustion metrics failed");
            }
        };

        try (PeachRpcClient client = client(
                (endpoint, frame, timeout) -> {
                    attempts.incrementAndGet();
                    return CompletableFuture.failedFuture(
                            new RpcUnavailableException("Endpoint unavailable"));
                },
                failingObserver)) {
            RetryService service = client.refer(
                    RetryService.class, "1.0.0", "test");
            CompletionException failure = assertThrows(
                    CompletionException.class,
                    () -> service.find("retry"));
            assertInstanceOf(
                    RpcUnavailableException.class,
                    failure.getCause());
            assertEquals(2, attempts.get());
        }
    }

    @Test
    void responseDecoderRunsOutsideTransportCompletionThread() throws Exception {
        CompletableFuture<byte[]> transportResponse = new CompletableFuture<>();
        AtomicReference<byte[]> capturedRequest = new AtomicReference<>();
        CountDownLatch requestSent = new CountDownLatch(1);
        CountDownLatch decodeEntered = new CountDownLatch(1);
        CountDownLatch releaseDecode = new CountDownLatch(1);
        AtomicReference<String> decodeThread = new AtomicReference<>();

        RpcCodec codec = new StringCodec(() -> {
            decodeThread.set(Thread.currentThread().getName());
            decodeEntered.countDown();
            try {
                if (!releaseDecode.await(3, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Decoder timed out");
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(error);
            }
        });

        try (PeachRpcClient client = PeachRpcClient.builder()
                .serviceDiscovery(new StaticDiscovery())
                .transportClient(new TestTransport((endpoint, frame, timeout) -> {
                    capturedRequest.set(frame);
                    requestSent.countDown();
                    return transportResponse;
                }))
                .codecRegistry(RpcCodecRegistry.of(codec))
                .timeout(Duration.ofSeconds(3))
                .build()) {
            RetryService service = client.refer(RetryService.class, "1.0.0", "test");
            CompletableFuture<String> call =
                    CompletableFuture.supplyAsync(() -> service.find("42"));
            assertTrue(requestSent.await(2, TimeUnit.SECONDS));

            Thread eventLoop = Thread.ofPlatform()
                    .name("simulated-transport-eventloop")
                    .start(() -> transportResponse.complete(
                            successResponse(capturedRequest.get(), "ok")));
            try {
                assertTrue(decodeEntered.await(2, TimeUnit.SECONDS));
                eventLoop.join(250);
                assertFalse(eventLoop.isAlive(),
                        "Transport completion must not block on decode");
                assertTrue(decodeThread.get()
                        .startsWith("peach-rpc-client-completion-"));
            } finally {
                releaseDecode.countDown();
            }
            assertEquals("ok", call.get(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void shouldRejectNewCallWhenCompletionCapacityIsExhausted()
            throws Exception {
        java.util.concurrent.CopyOnWriteArrayList<byte[]> frames =
                new java.util.concurrent.CopyOnWriteArrayList<>();
        java.util.concurrent.CopyOnWriteArrayList<CompletableFuture<byte[]>> replies =
                new java.util.concurrent.CopyOnWriteArrayList<>();
        CountDownLatch admitted = new CountDownLatch(2);
        try (PeachRpcClient client = PeachRpcClient.builder()
                .serviceDiscovery(new StaticDiscovery())
                .transportClient(new TestTransport(
                        (endpoint, frame, timeout) -> {
                            frames.add(frame);
                            CompletableFuture<byte[]> response =
                                    new CompletableFuture<>();
                            replies.add(response);
                            admitted.countDown();
                            return response;
                        }))
                .codecRegistry(RpcCodecRegistry.of(new StringCodec()))
                .timeout(Duration.ofSeconds(5))
                .responseCompletionThreads(1)
                .responseCompletionQueueCapacity(1)
                .build()) {
            RetryService service = client.refer(
                    RetryService.class, "1.0.0", "test");
            CompletableFuture<String> first =
                    CompletableFuture.supplyAsync(() -> service.find("one"));
            CompletableFuture<String> second =
                    CompletableFuture.supplyAsync(() -> service.find("two"));
            assertTrue(admitted.await(2, TimeUnit.SECONDS));

            CompletionException rejected = assertThrows(
                    CompletionException.class,
                    () -> service.find("three"));
            assertInstanceOf(
                    io.peach.rpc.api.RpcOverloadedException.class,
                    rejected.getCause());
            assertEquals(2, frames.size());

            for (int i = 0; i < replies.size(); i++) {
                replies.get(i).complete(
                        successResponse(frames.get(i), "ok"));
            }
            assertEquals("ok", first.get(2, TimeUnit.SECONDS));
            assertEquals("ok", second.get(2, TimeUnit.SECONDS));
        }
    }

    private static PeachRpcClient client(
            RequestFunction request) {
        return client(
                request,
                RpcObserver.noop());
    }

    private static PeachRpcClient client(
            RequestFunction request,
            RpcObserver observer) {
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
                .observer(observer)
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

        private final Runnable onDecode;

        private StringCodec() {
            this(() -> { });
        }

        private StringCodec(Runnable onDecode) {
            this.onDecode = onDecode;
        }

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
                    onDecode.run();
                    return new String(
                            payload,
                            StandardCharsets.UTF_8);
                }
            };
        }
    }
}
