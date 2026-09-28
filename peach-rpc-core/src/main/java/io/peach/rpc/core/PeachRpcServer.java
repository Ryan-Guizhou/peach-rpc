package io.peach.rpc.core;

import io.peach.rpc.api.RpcEndpoint;
import io.peach.rpc.api.RpcException;
import io.peach.rpc.api.RpcExecutionMode;
import io.peach.rpc.api.RpcIds;
import io.peach.rpc.api.RpcRemoteError;
import io.peach.rpc.api.RpcStatus;
import io.peach.rpc.api.ServiceInstance;
import io.peach.rpc.api.ServiceKey;
import io.peach.rpc.codec.RpcCodecRegistry;
import io.peach.rpc.codec.RpcMethodCodec;
import io.peach.rpc.codec.RpcCodecIds;
import io.peach.rpc.observability.RpcObserver;
import io.peach.rpc.protocol.RpcErrorCodec;
import io.peach.rpc.protocol.RpcFrameView;
import io.peach.rpc.protocol.RpcMessageType;
import io.peach.rpc.protocol.RpcProtocolCodec;
import io.peach.rpc.protocol.RpcProtocolException;
import io.peach.rpc.registry.ServiceRegistrar;
import io.peach.rpc.transport.RpcTransportServer;
import java.time.Duration;
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
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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
    private final String advertisedHost;
    private final int advertisedPort;
    private final Semaphore admission;
    private final Duration drainTimeout;
    private final Duration controlPlaneTimeout;
    private final RpcProviderExecutionOptions executionOptions;
    private final RpcObserver observer;
    private final ExecutorService blockingExecutor =
            Executors.newVirtualThreadPerTaskExecutor();
    private final ThreadPoolExecutor cpuExecutor;
    private final ConcurrentMap<Integer, ServiceBinding> bindings =
            new ConcurrentHashMap<>();
    private final List<ServiceInstance> configuredInstances =
            new CopyOnWriteArrayList<>();
    private final AtomicReference<State> state =
            new AtomicReference<>(State.NEW);

    private volatile RpcEndpoint actualEndpoint;
    private volatile RpcEndpoint advertisedEndpoint;

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
        this.advertisedHost = builder.advertisedHost;
        this.advertisedPort = builder.advertisedPort;
        this.admission = new Semaphore(builder.maxConcurrent);
        this.drainTimeout = Objects.requireNonNull(
                builder.drainTimeout,
                "drainTimeout");
        this.controlPlaneTimeout = Objects.requireNonNull(
                builder.controlPlaneTimeout,
                "controlPlaneTimeout");
        this.executionOptions = Objects.requireNonNull(
                builder.executionOptions,
                "executionOptions");
        this.observer = Objects.requireNonNull(
                builder.observer,
                "observer");
        this.cpuExecutor = new ThreadPoolExecutor(
                executionOptions.cpuParallelism(),
                executionOptions.cpuParallelism(),
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(
                        executionOptions.cpuQueueCapacity()),
                Thread.ofPlatform()
                        .name("peach-rpc-cpu-", 0)
                        .factory(),
                new ThreadPoolExecutor.AbortPolicy());
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
        ServiceBinding binding =
                new ServiceBinding(key, api, implementation, codecs);
        if (binding.usesDirectExecution()
                && !executionOptions.allowDirect()) {
            throw new IllegalStateException(
                    "DIRECT RPC execution is disabled; enable it explicitly before registering "
                            + api.getName());
        }
        ServiceBinding previous = bindings.putIfAbsent(
                serviceId,
                binding);
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
                    RpcEndpoint published;
                    try {
                        published = resolveAdvertisedEndpoint(endpoint);
                    } catch (RuntimeException error) {
                        state.set(State.CLOSED);
                        transport.close();
                        started.completeExceptionally(error);
                        return;
                    }
                    advertisedEndpoint = published;
                    registerConfiguredServices(published)
                            .whenComplete((ignored, registryError) -> {
                                if (registryError != null) {
                                    rollbackRegisteredServices(published)
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
                                        "Peach RPC server started: bind={}, advertised={}",
                                        endpoint.authority(),
                                        published.authority());
                                started.complete(endpoint);
                            });
                });
        return started;
    }

    private RpcEndpoint resolveAdvertisedEndpoint(RpcEndpoint endpoint) {
        String host = advertisedHost;
        if (host == null || host.isBlank()) {
            host = bindEndpoint.host();
            if (isWildcardHost(host)) {
                throw new IllegalStateException(
                        "RPC advertised host is required when bind host is " + host);
            }
        }
        int port = advertisedPort > 0
                ? advertisedPort
                : endpoint.port();
        if (port <= 0) {
            throw new IllegalStateException(
                    "RPC advertised port could not be resolved");
        }
        return new RpcEndpoint(host, port);
    }

    private static boolean isWildcardHost(String host) {
        return "0.0.0.0".equals(host)
                || "::".equals(host)
                || "[::]".equals(host);
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
                        registrations.toArray(CompletableFuture[]::new))
                .orTimeout(
                        controlPlaneTimeout.toMillis(),
                        TimeUnit.MILLISECONDS);
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
                        rollbacks.toArray(CompletableFuture[]::new))
                .orTimeout(
                        controlPlaneTimeout.toMillis(),
                        TimeUnit.MILLISECONDS);
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
        RpcExecutionMode executionMode;
        try {
            executionMode = binding.executionMode(request.methodId());
        } catch (NoSuchMethodException error) {
            admission.release();
            return CompletableFuture.completedFuture(
                    frameworkError(
                            request,
                            RpcStatus.METHOD_NOT_FOUND,
                            "Method not found"));
        }

        long observationStartedAtNanos =
                observer.enabled() ? System.nanoTime() : 0L;
        if (observer.enabled()) {
            result.whenComplete((responseBytes, error) ->
                    observeServerInvocation(
                            request,
                            executionMode,
                            observationStartedAtNanos,
                            responseBytes,
                            error));
        }

        if (executionMode == RpcExecutionMode.DIRECT) {
            execute(request, binding, methodCodec, result);
            return result;
        }

        ExecutorService selectedExecutor =
                executionMode == RpcExecutionMode.CPU
                        ? cpuExecutor
                        : blockingExecutor;
        Future<?> task;
        try {
            task = selectedExecutor.submit(() -> execute(
                    request,
                    binding,
                    methodCodec,
                    result));
        } catch (RejectedExecutionException error) {
            admission.release();
            return CompletableFuture.completedFuture(
                    errorResponse(
                            request,
                            RpcStatus.OVERLOADED,
                            RpcException.class.getName(),
                            "Provider execution queue is full"));
        }
        result.whenComplete((ignoredValue, ignoredError) -> {
            if (result.isCancelled()) {
                task.cancel(true);
            }
        });
        return result;
    }

    private void observeServerInvocation(
            RpcFrameView request,
            RpcExecutionMode executionMode,
            long startedAtNanos,
            byte[] responseBytes,
            Throwable error) {
        RpcStatus status = RpcStatus.INTERNAL_ERROR;
        if (error == null && responseBytes != null) {
            try {
                status = RpcProtocolCodec.view(responseBytes).status();
            } catch (RuntimeException ignored) {
                status = RpcStatus.INTERNAL_ERROR;
            }
        } else if (error instanceof java.util.concurrent.CancellationException) {
            status = RpcStatus.UNAVAILABLE;
        }
        observer.onServerInvocationCompleted(
                request.serviceId(),
                request.methodId(),
                executionMode,
                System.nanoTime() - startedAtNanos,
                status,
                error);
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
            if (result.isCancelled()) {
                return;
            }
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
        State current = state.get();
        if (current == State.CLOSED) {
            return;
        }

        boolean drain = current == State.STARTED
                && state.compareAndSet(State.STARTED, State.DRAINING);
        if (!drain) {
            state.set(State.CLOSED);
        }

        RpcEndpoint endpoint = advertisedEndpoint;
        if (endpoint != null) {
            for (ServiceInstance configured : configuredInstances) {
                try {
                    registrar.unregister(
                                    runtimeInstance(configured, endpoint))
                            .toCompletableFuture()
                            .orTimeout(
                                    controlPlaneTimeout.toMillis(),
                                    TimeUnit.MILLISECONDS)
                            .join();
                } catch (RuntimeException error) {
                    LOGGER.warn(
                            "Failed to unregister RPC service {}",
                            configured.serviceKey().canonicalName(),
                            error);
                }
            }
        }

        if (drain) {
            try {
                transport.drain(drainTimeout)
                        .toCompletableFuture()
                        .join();
            } catch (RuntimeException error) {
                LOGGER.warn(
                        "RPC graceful drain failed; forcing transport close",
                        error);
            } finally {
                state.set(State.CLOSED);
            }
        }
        transport.close();
        blockingExecutor.close();
        cpuExecutor.shutdownNow();
    }

    private enum State {
        NEW,
        STARTING,
        STARTED,
        DRAINING,
        CLOSED
    }

    /** Provider 运行时 Builder。 */
    public static final class Builder {
        private ServiceRegistrar registrar;
        private RpcTransportServer transport;
        private RpcCodecRegistry codecs;
        private RpcEndpoint bind;
        private String advertisedHost;
        private int advertisedPort;
        private int maxConcurrent = 4096;
        private Duration drainTimeout = Duration.ofSeconds(30);
        private Duration controlPlaneTimeout = Duration.ofSeconds(3);
        private RpcProviderExecutionOptions executionOptions =
                RpcProviderExecutionOptions.DEFAULT;
        private RpcObserver observer = RpcObserver.noop();

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
         * 设置向 Registry 发布的 Provider 主机地址。
         *
         * @param value 发布主机地址
         * @return Provider Builder
         */
        public Builder advertisedHost(String value) {
            this.advertisedHost = value == null
                    ? null
                    : value.trim();
            return this;
        }

        /**
         * 设置向 Registry 发布的 Provider 端口。
         *
         * @param value 发布端口，0 表示使用实际监听端口
         * @return Provider Builder
         */
        public Builder advertisedPort(int value) {
            if (value < 0 || value > 65_535) {
                throw new IllegalArgumentException(
                        "advertisedPort must be between 0 and 65535");
            }
            this.advertisedPort = value;
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
         * 设置 Provider 优雅排空超时时间。
         *
         * @param value 最大排空时间
         * @return Provider Builder
         */
        public Builder drainTimeout(Duration value) {
            if (value == null || value.isNegative() || value.isZero()) {
                throw new IllegalArgumentException(
                        "drainTimeout must be positive");
            }
            this.drainTimeout = value;
            return this;
        }

        /**
         * 设置 Registry 等控制面操作的最大等待时间。
         *
         * @param value 控制面操作超时时间
         * @return Provider Builder
         */
        public Builder controlPlaneTimeout(Duration value) {
            if (value == null || value.isNegative() || value.isZero()) {
                throw new IllegalArgumentException(
                        "controlPlaneTimeout must be positive");
            }
            this.controlPlaneTimeout = value;
            return this;
        }

        /**
         * 设置 Provider 业务执行资源策略。
         *
         * @param value 执行资源配置
         * @return Provider Builder
         */
        public Builder executionOptions(RpcProviderExecutionOptions value) {
            this.executionOptions = Objects.requireNonNull(
                    value,
                    "executionOptions");
            return this;
        }

        /**
         * 设置 Provider 可观测性 Observer。
         *
         * @param value Observer
         * @return Provider Builder
         */
        public Builder observer(RpcObserver value) {
            this.observer = Objects.requireNonNull(value, "observer");
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
