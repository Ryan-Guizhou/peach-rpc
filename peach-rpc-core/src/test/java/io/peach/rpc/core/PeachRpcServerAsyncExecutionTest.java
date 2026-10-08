package io.peach.rpc.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.peach.rpc.api.PeachRpcExecution;
import io.peach.rpc.api.RpcEndpoint;
import io.peach.rpc.api.RpcExecutionMode;
import io.peach.rpc.api.RpcIds;
import io.peach.rpc.api.RpcStatus;
import io.peach.rpc.api.ServiceInstance;
import io.peach.rpc.api.ServiceKey;
import io.peach.rpc.codec.RpcCodec;
import io.peach.rpc.codec.RpcCodecRegistry;
import io.peach.rpc.protocol.RpcProtocolCodec;
import io.peach.rpc.registry.ServiceRegistrar;
import io.peach.rpc.transport.RpcRequestHandler;
import io.peach.rpc.transport.RpcTransportServer;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class PeachRpcServerAsyncExecutionTest {

    @Test
    void asyncStageShouldNotBlockCpuWorker() throws Exception {
        CapturingTransportServer transport = new CapturingTransportServer();
        AsyncServiceImpl service = new AsyncServiceImpl();
        PeachRpcServer server = createServer(
                transport,
                service,
                2,
                19093);

        try {
            server.start().toCompletableFuture().join();

            CompletionStage<byte[]> slowResponse =
                    transport.handle(request("slow"));
            assertFalse(slowResponse.toCompletableFuture().isDone());

            byte[] fastResponse = transport.handle(request("fast"))
                    .toCompletableFuture()
                    .get(1, TimeUnit.SECONDS);
            assertEquals(
                    RpcStatus.OK,
                    RpcProtocolCodec.view(fastResponse).status());

            service.slow.complete("slow");
            byte[] completedSlowResponse =
                    slowResponse.toCompletableFuture()
                            .get(1, TimeUnit.SECONDS);
            assertEquals(
                    RpcStatus.OK,
                    RpcProtocolCodec.view(completedSlowResponse).status());
        } finally {
            server.close();
        }
    }

    @Test
    void asyncCompletionShouldEncodeOnProviderExecutor() throws Exception {
        CapturingTransportServer transport = new CapturingTransportServer();
        AsyncServiceImpl service = new AsyncServiceImpl();
        TrackingCodec codec = new TrackingCodec();
        PeachRpcServer server = createServer(
                transport,
                service,
                2,
                19095,
                codec);

        try {
            server.start().toCompletableFuture().join();

            CompletionStage<byte[]> response =
                    transport.handle(request("slow"));
            assertFalse(response.toCompletableFuture().isDone());
            assertTrue(
                    service.slowInvoked.await(
                            1,
                            TimeUnit.SECONDS));

            Thread completer = Thread.ofPlatform()
                    .name("foreign-async-completion")
                    .start(() -> service.slow.complete("slow"));
            completer.join();

            byte[] completed = response.toCompletableFuture()
                    .get(1, TimeUnit.SECONDS);
            assertEquals(
                    RpcStatus.OK,
                    RpcProtocolCodec.view(completed).status());
            assertTrue(
                    codec.encodeThreadName.get()
                            .startsWith("peach-rpc-cpu-"));
        } finally {
            server.close();
        }
    }

    @Test
    void asyncCompletionShouldFailFastWhenCpuCompletionQueueIsFull()
            throws Exception {
        CapturingTransportServer transport = new CapturingTransportServer();
        AsyncServiceImpl service = new AsyncServiceImpl();
        PeachRpcServer server = createServer(
                transport,
                service,
                3,
                19096);

        try {
            server.start().toCompletableFuture().join();

            CompletionStage<byte[]> slowResponse =
                    transport.handle(request("slow"));
            assertFalse(slowResponse.toCompletableFuture().isDone());
            assertTrue(
                    service.slowInvoked.await(
                            1,
                            TimeUnit.SECONDS));

            CompletionStage<byte[]> blockingResponse =
                    transport.handle(request("blocking"));
            assertTrue(
                    service.blockingEntered.await(
                            1,
                            TimeUnit.SECONDS));

            CompletionStage<byte[]> queuedResponse =
                    transport.handle(request("queued"));
            assertFalse(queuedResponse.toCompletableFuture().isDone());

            service.slow.complete("slow");
            byte[] overloaded = slowResponse.toCompletableFuture()
                    .get(1, TimeUnit.SECONDS);
            assertEquals(
                    RpcStatus.OVERLOADED,
                    RpcProtocolCodec.view(overloaded).status());

            service.releaseBlocking.countDown();

            assertEquals(
                    RpcStatus.OK,
                    RpcProtocolCodec.view(
                            blockingResponse.toCompletableFuture()
                                    .get(1, TimeUnit.SECONDS))
                            .status());
            assertEquals(
                    RpcStatus.OK,
                    RpcProtocolCodec.view(
                            queuedResponse.toCompletableFuture()
                                    .get(1, TimeUnit.SECONDS))
                            .status());

            byte[] recovered = transport.handle(request("fast"))
                    .toCompletableFuture()
                    .get(1, TimeUnit.SECONDS);
            assertEquals(
                    RpcStatus.OK,
                    RpcProtocolCodec.view(recovered).status());
        } finally {
            service.releaseBlocking.countDown();
            server.close();
        }
    }

    @Test
    void asyncStageShouldRetainAdmissionUntilCompletion() throws Exception {
        CapturingTransportServer transport = new CapturingTransportServer();
        AsyncServiceImpl service = new AsyncServiceImpl();
        PeachRpcServer server = createServer(
                transport,
                service,
                1,
                19094);

        try {
            server.start().toCompletableFuture().join();

            CompletionStage<byte[]> slowResponse =
                    transport.handle(request("slow"));
            assertFalse(slowResponse.toCompletableFuture().isDone());

            byte[] overloaded = transport.handle(request("fast"))
                    .toCompletableFuture()
                    .get(1, TimeUnit.SECONDS);
            assertEquals(
                    RpcStatus.OVERLOADED,
                    RpcProtocolCodec.view(overloaded).status());

            service.slow.complete("slow");
            byte[] completedSlowResponse =
                    slowResponse.toCompletableFuture()
                            .get(1, TimeUnit.SECONDS);
            assertEquals(
                    RpcStatus.OK,
                    RpcProtocolCodec.view(completedSlowResponse).status());

            byte[] recovered = transport.handle(request("fast"))
                    .toCompletableFuture()
                    .get(1, TimeUnit.SECONDS);
            assertEquals(
                    RpcStatus.OK,
                    RpcProtocolCodec.view(recovered).status());
        } finally {
            server.close();
        }
    }

    @Test
    void busyServiceCannotBlockAnotherServiceAndCancellationReleasesAdmission()
            throws Exception {
        CapturingTransportServer transport = new CapturingTransportServer();
        AsyncServiceImpl busy = new AsyncServiceImpl();
        PeachRpcServer server = PeachRpcServer.builder()
                .serviceRegistrar(new NoopRegistry())
                .transportServer(transport)
                .codecRegistry(RpcCodecRegistry.of(new NoopCodec()))
                .bindEndpoint(new RpcEndpoint("127.0.0.1", 19097))
                .maxConcurrent(2)
                .admissionOptions(new RpcProviderAdmissionOptions(
                        1024L, 0, 0, 0L, 0L))
                .executionOptions(new RpcProviderExecutionOptions(
                        false, 1, 4))
                .build()
                .registerService(
                        AsyncService.class,
                        busy,
                        "1.0.0",
                        "default")
                .registerService(
                        AnotherService.class,
                        new AnotherServiceImpl(),
                        "1.0.0",
                        "default");
        try {
            server.start().toCompletableFuture().join();

            CompletableFuture<byte[]> outstanding =
                    transport.handle(request("slow")).toCompletableFuture();
            assertTrue(busy.slowInvoked.await(1, TimeUnit.SECONDS));
            assertFalse(outstanding.isDone());

            byte[] firstServiceOverload = transport.handle(request("fast"))
                    .toCompletableFuture()
                    .get(1, TimeUnit.SECONDS);
            assertEquals(
                    RpcStatus.OVERLOADED,
                    RpcProtocolCodec.view(firstServiceOverload).status());

            ServiceKey otherKey = new ServiceKey(
                    AnotherService.class.getName(), "1.0.0", "default");
            byte[] secondServiceRequest = RpcProtocolCodec.encodeRequest(
                    (byte) 1,
                    RpcIds.serviceId(otherKey),
                    RpcIds.methodId(AnotherService.class.getMethod("fast")),
                    0L,
                    0L,
                    Map.of(),
                    new byte[0]);
            byte[] otherResponse = transport.handle(secondServiceRequest)
                    .toCompletableFuture()
                    .get(1, TimeUnit.SECONDS);
            assertEquals(
                    RpcStatus.OK,
                    RpcProtocolCodec.view(otherResponse).status());

            assertTrue(outstanding.cancel(true));
            busy.slow.complete("cancelled");
            byte[] recovered = transport.handle(request("fast"))
                    .toCompletableFuture()
                    .get(1, TimeUnit.SECONDS);
            assertEquals(
                    RpcStatus.OK,
                    RpcProtocolCodec.view(recovered).status());
        } finally {
            busy.releaseBlocking.countDown();
            server.close();
        }
    }

    public interface AnotherService {
        String fast();
    }

    public static final class AnotherServiceImpl implements AnotherService {
        @Override
        public String fast() {
            return "other";
        }
    }

    private static PeachRpcServer createServer(
            CapturingTransportServer transport,
            AsyncServiceImpl service,
            int maxConcurrent,
            int port) {
        return createServer(
                transport,
                service,
                maxConcurrent,
                port,
                new NoopCodec());
    }

    private static PeachRpcServer createServer(
            CapturingTransportServer transport,
            AsyncServiceImpl service,
            int maxConcurrent,
            int port,
            RpcCodec codec) {
        return PeachRpcServer.builder()
                .serviceRegistrar(new NoopRegistry())
                .transportServer(transport)
                .codecRegistry(RpcCodecRegistry.of(codec))
                .bindEndpoint(new RpcEndpoint("127.0.0.1", port))
                .maxConcurrent(maxConcurrent)
                .executionOptions(new RpcProviderExecutionOptions(
                        false,
                        1,
                        1))
                .build()
                .registerService(
                        AsyncService.class,
                        service,
                        "1.0.0",
                        "default");
    }

    private static byte[] request(String methodName) throws Exception {
        ServiceKey key =
                new ServiceKey(
                        AsyncService.class.getName(),
                        "1.0.0",
                        "default");
        Method method = AsyncService.class.getMethod(methodName);
        return RpcProtocolCodec.encodeRequest(
                (byte) 1,
                RpcIds.serviceId(key),
                RpcIds.methodId(method),
                0L,
                0L,
                Map.of(),
                new byte[0]);
    }

    public interface AsyncService {

        @PeachRpcExecution(RpcExecutionMode.CPU)
        CompletionStage<String> slow();

        @PeachRpcExecution(RpcExecutionMode.CPU)
        String fast();

        @PeachRpcExecution(RpcExecutionMode.CPU)
        String blocking();

        @PeachRpcExecution(RpcExecutionMode.CPU)
        String queued();
    }

    public static final class AsyncServiceImpl implements AsyncService {
        private final CompletableFuture<String> slow =
                new CompletableFuture<>();
        private final CountDownLatch slowInvoked =
                new CountDownLatch(1);
        private final CountDownLatch blockingEntered =
                new CountDownLatch(1);
        private final CountDownLatch releaseBlocking =
                new CountDownLatch(1);

        @Override
        public CompletionStage<String> slow() {
            slowInvoked.countDown();
            return slow;
        }

        @Override
        public String fast() {
            return "fast";
        }

        @Override
        public String blocking() {
            blockingEntered.countDown();
            try {
                if (!releaseBlocking.await(
                        2,
                        TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "Blocking test method timed out");
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(error);
            }
            return "blocking";
        }

        @Override
        public String queued() {
            return "queued";
        }
    }

    private static final class TrackingCodec implements RpcCodec {
        private final AtomicReference<String> encodeThreadName =
                new AtomicReference<>();

        @Override
        public byte code() {
            return 1;
        }

        @Override
        public byte[] encode(Object value) {
            encodeThreadName.set(Thread.currentThread().getName());
            return new byte[0];
        }

        @Override
        public <T> T decode(byte[] bytes, Class<T> type) {
            if (type == Object[].class) {
                return type.cast(new Object[0]);
            }
            return null;
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
            if (type == Object[].class) {
                return type.cast(new Object[0]);
            }
            return null;
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

    private static final class CapturingTransportServer
            implements RpcTransportServer {
        private final AtomicReference<RpcRequestHandler> handler =
                new AtomicReference<>();

        @Override
        public CompletionStage<RpcEndpoint> start(
                RpcEndpoint bind,
                RpcRequestHandler requestHandler) {
            handler.set(requestHandler);
            return CompletableFuture.completedFuture(bind);
        }

        CompletionStage<byte[]> handle(byte[] request) {
            return handler.get().handle(
                    new RpcEndpoint("127.0.0.1", 20000),
                    request);
        }

        @Override
        public void close() {
        }
    }
}
