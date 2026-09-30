package io.peach.rpc.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

    private static PeachRpcServer createServer(
            CapturingTransportServer transport,
            AsyncServiceImpl service,
            int maxConcurrent,
            int port) {
        return PeachRpcServer.builder()
                .serviceRegistrar(new NoopRegistry())
                .transportServer(transport)
                .codecRegistry(RpcCodecRegistry.of(new NoopCodec()))
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
    }

    public static final class AsyncServiceImpl implements AsyncService {
        private final CompletableFuture<String> slow =
                new CompletableFuture<>();

        @Override
        public CompletionStage<String> slow() {
            return slow;
        }

        @Override
        public String fast() {
            return "fast";
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
