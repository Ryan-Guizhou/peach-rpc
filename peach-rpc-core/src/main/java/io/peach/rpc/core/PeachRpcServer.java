package io.peach.rpc.core;

import io.peach.rpc.api.RpcEndpoint;
import io.peach.rpc.api.RpcException;
import io.peach.rpc.api.RpcIds;
import io.peach.rpc.api.RpcRemoteError;
import io.peach.rpc.api.RpcStatus;
import io.peach.rpc.api.ServiceInstance;
import io.peach.rpc.api.ServiceKey;
import io.peach.rpc.codec.RpcCodecRegistry;
import io.peach.rpc.codec.RpcMethodCodec;
import io.peach.rpc.codec.RpcCodecIds;
import io.peach.rpc.protocol.RpcErrorCodec;
import io.peach.rpc.protocol.RpcFrameView;
import io.peach.rpc.protocol.RpcMessageType;
import io.peach.rpc.protocol.RpcProtocolCodec;
import io.peach.rpc.protocol.RpcProtocolException;
import io.peach.rpc.registry.ServiceRegistrar;
import io.peach.rpc.transport.RpcTransportServer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Peach RPC Provider 运行时。 */
public final class PeachRpcServer implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(PeachRpcServer.class);

    private final ServiceRegistrar registrar;
    private final RpcTransportServer transport;
    private final RpcCodecRegistry codecs;
    private final RpcEndpoint bindEndpoint;
    private final Semaphore admission;
    private final ExecutorService executor =
            Executors.newVirtualThreadPerTaskExecutor();
    private final ConcurrentMap<Integer, ServiceBinding> bindings =
            new ConcurrentHashMap<>();
    private final List<ServiceInstance> configuredInstances =
            new CopyOnWriteArrayList<>();
    private final AtomicReference<State> state =
            new AtomicReference<>(State.NEW);

    private volatile RpcEndpoint actualEndpoint;

    private PeachRpcServer(Builder builder) {
        this.registrar = Objects.requireNonNull(
                builder.registrar,
                "serviceRegistrar");
        this.transport = Objects.requireNonNull(
                builder.transport,
                "transportServer");
        this.codecs = Objects.requireNonNull(
                builder.codecs,
                "codecRegistry");
        this.bindEndpoint = Objects.requireNonNull(
                builder.bind,
                "bindEndpoint");
        this.admission = new Semaphore(builder.maxConcurrent);
    }

    /**
     * 创建 Provider Builder。
     *
     * @return Provider Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 注册本地服务实现。
     *
     * @param api 服务接口
     * @param implementation 服务实现
     * @param version 服务版本
     * @param group 服务分组
     * @return 当前 Server
     */
    public PeachRpcServer registerService(
            Class<?> api,
            Object implementation,
            String version,
            String group) {
        if (state.get() != State.NEW) {
            throw new IllegalStateException(
                    "Services can only be registered before server start");
        }
        Objects.requireNonNull(api, "api");
        Objects.requireNonNull(implementation, "implementation");
        if (!api.isInstance(implementation)) {
            throw new IllegalArgumentException(
                    "Service implementation does not implement " + api.getName());
        }

        ServiceKey key = new ServiceKey(api.getName(), version, group);
        int serviceId = RpcIds.serviceId(key);
        ServiceBinding previous = bindings.putIfAbsent(
                serviceId,
                new ServiceBinding(key, api, implementation, codecs));
        if (previous != null) {
            throw new IllegalStateException(
                    "Service id collision: " + serviceId);
        }
        configuredInstances.add(new ServiceInstance(
                UUID.randomUUID().toString(),
                key,
                bindEndpoint,
                100,
                Map.of()));
        return this;
    }

    /**
     * 启动 TCP Server 并注册所有服务实例。
     *
     * @return 异步启动结果，成功时返回实际监听端点
     */
    public CompletionStage<RpcEndpoint> start() {
        if (!state.compareAndSet(State.NEW, State.STARTING)) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException(
                            "RPC server has already been started or closed"));
        }

        CompletableFuture<RpcEndpoint> started = new CompletableFuture<>();
        transport.start(bindEndpoint, this::handle)
                .whenComplete((endpoint, transportError) -> {
                    if (transportError != null) {
                        state.set(State.CLOSED);
                        started.completeExceptionally(transportError);
                        return;
                    }
                    actualEndpoint = endpoint;
                    registerConfiguredServices(endpoint)
                            .whenComplete((ignored, registryError) -> {
                                if (registryError != null) {
                                    rollbackRegisteredServices(endpoint)
                                            .whenComplete((rollbackIgnored, rollbackError) -> {
                                                state.set(State.CLOSED);
                                                transport.close();
                                                if (rollbackError != null) {
                                                    registryError.addSuppressed(rollbackError);
                                                }
                                                started.completeExceptionally(registryError);
                                            });
                                    return;
                                }
                                state.set(State.STARTED);
                                LOGGER.info(
                                        "Peach RPC server started at {}",
                                        endpoint.authority());
                                started.complete(endpoint);
                            });
                });
        return started;
    }

    private CompletionStage<Void> registerConfiguredServices(
            RpcEndpoint endpoint) {
        List<CompletableFuture<Void>> registrations = new ArrayList<>();
        for (ServiceInstance configured : configuredInstances) {
            registrations.add(registrar.register(
                            runtimeInstance(configured, endpoint))
                    .toCompletableFuture());
        }
        return CompletableFuture.allOf(
                registrations.toArray(CompletableFuture[]::new));
    }

    private CompletionStage<Void> rollbackRegisteredServices(
            RpcEndpoint endpoint) {
        List<CompletableFuture<Void>> rollbacks = new ArrayList<>();
        for (ServiceInstance configured : configuredInstances) {
            CompletableFuture<Void> rollback = registrar
                    .unregister(runtimeInstance(configured, endpoint))
                    .exceptionally(error -> {
                        LOGGER.warn(
                                "Failed to rollback RPC service registration: service={}",
                                configured.serviceKey().canonicalName(),
                                error);
                        return null;
                    })
                    .toCompletableFuture();
            rollbacks.add(rollback);
        }
        return CompletableFuture.allOf(
                rollbacks.toArray(CompletableFuture[]::new));
    }

    private CompletionStage<byte[]> handle(
            RpcEndpoint remote,
            byte[] rawFrame) {
        RpcFrameView request;
        try {
            request = RpcProtocolCodec.view(rawFrame);
        } catch (RuntimeException error) {
            return CompletableFuture.failedFuture(error);
        }

        if (request.messageType() != RpcMessageType.REQUEST) {
            return CompletableFuture.failedFuture(
                    new RpcProtocolException("Expected request frame"));
        }
        if (state.get() != State.STARTED) {
            return CompletableFuture.completedFuture(
                    frameworkError(
                            request,
                            RpcStatus.UNAVAILABLE,
                            "Server is not ready"));
        }

        long deadline;
        try {
            deadline = request.deadlineEpochMillis();
        } catch (RpcProtocolException error) {
            return CompletableFuture.completedFuture(
                    frameworkError(
                            request,
                            RpcStatus.BAD_REQUEST,
                            "Invalid deadline"));
        }
        if (deadline > 0 && System.currentTimeMillis() > deadline) {
            return CompletableFuture.completedFuture(
                    frameworkError(
                            request,
                            RpcStatus.DEADLINE_EXCEEDED,
                            "Deadline exceeded"));
        }

        ServiceBinding binding = bindings.get(request.serviceId());
        if (binding == null) {
            return CompletableFuture.completedFuture(
                    frameworkError(
                            request,
                            RpcStatus.SERVICE_NOT_FOUND,
                            "Service not found"));
        }

        RpcMethodCodec methodCodec;
        try {
            methodCodec = binding.codec(
                    request.methodId(),
                    request.codec());
        } catch (NoSuchMethodException error) {
            return CompletableFuture.completedFuture(
                    frameworkError(
                            request,
                            RpcStatus.METHOD_NOT_FOUND,
                            "Method not found"));
        } catch (IllegalArgumentException error) {
            return CompletableFuture.completedFuture(
                    frameworkError(
                            request,
                            RpcStatus.BAD_REQUEST,
                            "Unsupported codec"));
        }

        if (!admission.tryAcquire()) {
            return CompletableFuture.completedFuture(
                    errorResponse(
                            request,
                            RpcStatus.OVERLOADED,
                            RpcException.class.getName(),
                            "Server overloaded"));
        }

        CompletableFuture<byte[]> result = new CompletableFuture<>();
        executor.submit(() -> execute(
                request,
                binding,
                methodCodec,
                result));
        return result;
    }

    private void execute(
            RpcFrameView request,
            ServiceBinding binding,
            RpcMethodCodec methodCodec,
            CompletableFuture<byte[]> result) {
        try {
            Object[] arguments = methodCodec.decodeArguments(
                    request.bytes(),
                    request.payloadOffset(),
                    request.payloadLength());
            Object value = binding.invoke(
                    request.methodId(),
                    arguments);
            if (value instanceof CompletionStage<?> stage) {
                value = stage.toCompletableFuture().join();
            }
            result.complete(response(
                    request,
                    methodCodec.codecId(),
                    RpcStatus.OK,
                    methodCodec.encodeResult(value)));
        } catch (Throwable error) {
            LOGGER.warn(
                    "RPC service invocation failed: requestId={}, serviceId={}, methodId={}",
                    request.requestId(),
                    request.serviceId(),
                    request.methodId(),
                    error);
            result.complete(errorResponse(
                    request,
                    RpcStatus.BUSINESS_ERROR,
                    error.getClass().getName(),
                    "Remote service invocation failed"));
        } finally {
            admission.release();
        }
    }

    private static byte[] frameworkError(
            RpcFrameView request,
            RpcStatus status,
            String message) {
        return errorResponse(
                request,
                status,
                RpcException.class.getName(),
                message);
    }

    private static byte[] errorResponse(
            RpcFrameView request,
            RpcStatus status,
            String errorType,
            String message) {
        return response(
                request,
                RpcCodecIds.CONTROL,
                status,
                RpcErrorCodec.encode(new RpcRemoteError(errorType, message)));
    }

    private static byte[] response(
            RpcFrameView request,
            byte codecId,
            RpcStatus status,
            byte[] payload) {
        return RpcProtocolCodec.encodeResponse(
                codecId,
                status,
                request.requestId(),
                request.serviceId(),
                request.methodId(),
                payload);
    }

    private static ServiceInstance runtimeInstance(
            ServiceInstance configured,
            RpcEndpoint endpoint) {
        return new ServiceInstance(
                configured.instanceId(),
                configured.serviceKey(),
                endpoint,
                configured.weight(),
                configured.metadata());
    }

    @Override
    public void close() {
        State previous = state.getAndSet(State.CLOSED);
        if (previous == State.CLOSED) {
            return;
        }
        RpcEndpoint endpoint = actualEndpoint;
        if (endpoint != null) {
            for (ServiceInstance configured : configuredInstances) {
                try {
                    registrar.unregister(
                                    runtimeInstance(configured, endpoint))
                            .toCompletableFuture()
                            .join();
                } catch (RuntimeException error) {
                    LOGGER.warn(
                            "Failed to unregister RPC service {}",
                            configured.serviceKey().canonicalName(),
                            error);
                }
            }
        }
        transport.close();
        executor.close();
    }

    private enum State {
        NEW,
        STARTING,
        STARTED,
        CLOSED
    }

    /** Provider 运行时 Builder。 */
    public static final class Builder {
        private ServiceRegistrar registrar;
        private RpcTransportServer transport;
        private RpcCodecRegistry codecs;
        private RpcEndpoint bind;
        private int maxConcurrent = 4096;

        /** 创建 Provider Builder。 */
        public Builder() {
        }

        /**
         * 设置 Provider 服务注册控制面。
         *
         * @param value 服务注册控制面
         * @return Provider Builder
         */
        public Builder serviceRegistrar(ServiceRegistrar value) {
            this.registrar = value;
            return this;
        }

        /**
         * 设置 Transport Server。
         *
         * @param value Transport Server
         * @return Provider Builder
         */
        public Builder transportServer(RpcTransportServer value) {
            this.transport = value;
            return this;
        }

        /**
         * 设置 Codec 注册表。
         *
         * @param value Codec 注册表
         * @return Provider Builder
         */
        public Builder codecRegistry(RpcCodecRegistry value) {
            this.codecs = value;
            return this;
        }

        /**
         * 设置 Provider 绑定端点。
         *
         * @param value Provider 绑定端点
         * @return Provider Builder
         */
        public Builder bindEndpoint(RpcEndpoint value) {
            this.bind = value;
            return this;
        }

        /**
         * 设置 Provider 最大并发业务请求数。
         *
         * @param value 最大并发业务请求数
         * @return Provider Builder
         */
        public Builder maxConcurrent(int value) {
            if (value <= 0) {
                throw new IllegalArgumentException(
                        "maxConcurrent must be positive");
            }
            this.maxConcurrent = value;
            return this;
        }

        /**
         * 创建 Provider 运行时。
         *
         * @return Provider 运行时
         */
        public PeachRpcServer build() {
            return new PeachRpcServer(this);
        }
    }
}
