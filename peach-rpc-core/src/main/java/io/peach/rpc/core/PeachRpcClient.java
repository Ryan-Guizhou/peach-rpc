package io.peach.rpc.core;

import io.peach.rpc.api.PeachRpcIdempotent;
import io.peach.rpc.api.RpcEndpoint;
import io.peach.rpc.api.RpcException;
import io.peach.rpc.api.RpcMethodDescriptor;
import io.peach.rpc.api.RpcOverloadedException;
import io.peach.rpc.api.RpcRemoteException;
import io.peach.rpc.api.RpcStatus;
import io.peach.rpc.api.RpcUnavailableException;
import io.peach.rpc.api.ServiceInstance;
import io.peach.rpc.api.ServiceKey;
import io.peach.rpc.codec.RpcCodecRegistry;
import io.peach.rpc.codec.RpcMethodCodec;
import io.peach.rpc.generated.RpcGeneratedClients;
import io.peach.rpc.generated.RpcGeneratedInvocation;
import io.peach.rpc.loadbalance.LoadBalanceMetrics;
import io.peach.rpc.loadbalance.LoadBalancer;
import io.peach.rpc.protocol.RpcErrorCodec;
import io.peach.rpc.protocol.RpcFrameView;
import io.peach.rpc.protocol.RpcProtocolCodec;
import io.peach.rpc.proxy.ProxyFactory;
import io.peach.rpc.registry.ServiceDiscovery;
import io.peach.rpc.spi.ExtensionLoader;
import io.peach.rpc.transport.RpcTransportClient;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/** Peach RPC Consumer 运行时。 */
public final class PeachRpcClient implements AutoCloseable {
    private final ServiceDiscovery discovery;
    private final RpcTransportClient transport;
    private final RpcCodecRegistry codecs;
    private final byte defaultCodecId;
    private final LoadBalancer loadBalancer;
    private final ProxyFactory proxyFactory;
    private final Duration timeout;
    private final RpcClientResilienceOptions resilienceOptions;
    private final RetryBudget retryBudget;
    private final ConcurrentMap<ServiceKey, ServiceDirectory> directories =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<RpcEndpoint, EndpointStats> stats =
            new ConcurrentHashMap<>();
    private final LoadBalanceMetrics loadMetrics =
            new LoadBalanceMetrics() {
                @Override
                public long ewmaLatencyNanos(
                        ServiceInstance instance) {
                    return endpointStats(instance).ewma();
                }

                @Override
                public int inflight(
                        ServiceInstance instance) {
                    return endpointStats(instance).inflight();
                }

                @Override
                public boolean available(
                        ServiceInstance instance) {
                    return endpointStats(instance).available();
                }
            };

    private PeachRpcClient(Builder builder) {
        this.discovery = Objects.requireNonNull(builder.discovery, "serviceDiscovery");
        this.transport = Objects.requireNonNull(builder.transport, "transportClient");
        this.codecs = Objects.requireNonNull(builder.codecs, "codecRegistry");
        this.defaultCodecId = codecs.defaultCodec().code();
        this.loadBalancer = builder.loadBalancer != null
                ? builder.loadBalancer
                : ExtensionLoader.getLoader(LoadBalancer.class).getDefaultExtension();
        this.proxyFactory = builder.proxyFactory != null
                ? builder.proxyFactory
                : ExtensionLoader.getLoader(ProxyFactory.class).getDefaultExtension();
        this.timeout = Objects.requireNonNull(builder.timeout, "timeout");
        this.resilienceOptions = Objects.requireNonNull(
                builder.resilienceOptions,
                "resilienceOptions");
        this.retryBudget = new RetryBudget(resilienceOptions);
    }

    /**
     * 创建 Consumer Builder。
     *
     * @return Consumer Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 创建指定服务的 Consumer 引用。
     *
     * <p>服务标识、方法标识、方法 Codec 与本地目录均在此阶段预解析。
     * 若编译期 Generated Stub 存在则优先使用，否则回退到配置的 ProxyFactory。
     *
     * @param <T> 服务接口类型
     * @param api 服务接口
     * @param version 服务版本
     * @param group 服务分组
     * @return RPC 服务代理
     */
    public <T> T refer(Class<T> api, String version, String group) {
        Objects.requireNonNull(api, "api");
        ServiceKey key = new ServiceKey(api.getName(), version, group);
        ServiceDirectory directory = directories.computeIfAbsent(
                key,
                ignored -> new ServiceDirectory(discovery, key));
        ClientReference reference = ClientReference.create(
                key,
                api,
                directory,
                codecs,
                defaultCodecId,
                resilienceOptions);

        return RpcGeneratedClients.find(api)
                .map(factory -> factory.create(
                        new GeneratedInvocation(reference)))
                .orElseGet(() -> proxyFactory.create(
                        api,
                        (method, arguments) -> invokeN(
                                reference,
                                reference.require(method),
                                arguments)));
    }

    private CompletionStage<Object> invoke0(
            ClientReference reference,
            ClientMethodBinding method) {
        return invokeEncoded(
                reference,
                method,
                method.codec().encode0());
    }

    private CompletionStage<Object> invoke1(
            ClientReference reference,
            ClientMethodBinding method,
            Object argument0) {
        return invokeEncoded(
                reference,
                method,
                method.codec().encode1(argument0));
    }

    private CompletionStage<Object> invoke2(
            ClientReference reference,
            ClientMethodBinding method,
            Object argument0,
            Object argument1) {
        return invokeEncoded(
                reference,
                method,
                method.codec().encode2(argument0, argument1));
    }

    private CompletionStage<Object> invoke3(
            ClientReference reference,
            ClientMethodBinding method,
            Object argument0,
            Object argument1,
            Object argument2) {
        return invokeEncoded(
                reference,
                method,
                method.codec().encode3(
                        argument0,
                        argument1,
                        argument2));
    }

    private CompletionStage<Object> invoke4(
            ClientReference reference,
            ClientMethodBinding method,
            Object argument0,
            Object argument1,
            Object argument2,
            Object argument3) {
        return invokeEncoded(
                reference,
                method,
                method.codec().encode4(
                        argument0,
                        argument1,
                        argument2,
                        argument3));
    }

    private CompletionStage<Object> invokeN(
            ClientReference reference,
            ClientMethodBinding method,
            Object[] arguments) {
        return invokeEncoded(
                reference,
                method,
                method.codec().encodeArguments(arguments));
    }

    private CompletionStage<Object> invokeEncoded(
            ClientReference reference,
            ClientMethodBinding method,
            byte[] encodedArguments) {
        retryBudget.onRequest();
        if (!method.circuitBreaker().tryAcquire()) {
            return CompletableFuture.failedFuture(
                    new RpcUnavailableException(
                            "RPC circuit is open for "
                                    + reference.key().canonicalName()
                                    + '#'
                                    + method.methodId()));
        }

        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        CompletableFuture<Object> result = new CompletableFuture<>();
        result.whenComplete((ignoredValue, ignoredError) -> {
            if (result.isCancelled()) {
                method.circuitBreaker().onCancelled();
            }
        });
        attempt(
                reference,
                method,
                encodedArguments,
                deadlineNanos,
                1,
                result);
        return result;
    }

    private void attempt(
            ClientReference reference,
            ClientMethodBinding method,
            byte[] encodedArguments,
            long deadlineNanos,
            int attempt,
            CompletableFuture<Object> result) {
        if (result.isDone()) {
            return;
        }
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0L) {
            method.circuitBreaker().onFailure();
            result.completeExceptionally(
                    new io.peach.rpc.api.RpcTimeoutException(
                            "RPC request deadline exceeded"));
            return;
        }

        ServiceInstance[] instances = reference.directory().snapshot();
        ServiceInstance selected = instances.length == 0
                ? null
                : loadBalancer.select(instances, loadMetrics);
        if (selected == null) {
            retryOrComplete(
                    reference,
                    method,
                    encodedArguments,
                    deadlineNanos,
                    attempt,
                    result,
                    new RpcUnavailableException(
                            "No available instance for "
                                    + reference.key().canonicalName()));
            return;
        }

        EndpointStats endpointStats = endpointStats(selected);
        long startedAtNanos = System.nanoTime();
        endpointStats.begin();

        long remainingMillis = Math.max(
                1L,
                TimeUnit.NANOSECONDS.toMillis(remainingNanos));
        long deadlineEpochMillis =
                System.currentTimeMillis() + remainingMillis;
        byte[] request = RpcProtocolCodec.encodeRequest(
                method.codec().codecId(),
                reference.serviceId(),
                method.methodId(),
                deadlineEpochMillis,
                encodedArguments);

        CompletableFuture<byte[]> transportFuture = transport.request(
                        selected.endpoint(),
                        request,
                        Duration.ofNanos(remainingNanos))
                .toCompletableFuture();
        result.whenComplete((ignoredValue, ignoredError) -> {
            if (result.isCancelled()) {
                transportFuture.cancel(true);
            }
        });

        transportFuture.whenComplete((rawResponse, transportError) -> {
            long elapsed = System.nanoTime() - startedAtNanos;
            if (result.isCancelled()) {
                endpointStats.endCancelled(elapsed);
                return;
            }
            if (transportError != null) {
                Throwable failure = unwrap(transportError);
                if (failure instanceof CancellationException) {
                    endpointStats.endCancelled(elapsed);
                    return;
                }
                endpointStats.endFailure(elapsed, resilienceOptions);
                retryOrComplete(
                        reference,
                        method,
                        encodedArguments,
                        deadlineNanos,
                        attempt,
                        result,
                        failure);
                return;
            }

            try {
                Object value = decodeResponse(method, rawResponse);
                endpointStats.endSuccess(elapsed);
                method.circuitBreaker().onSuccess();
                result.complete(value);
            } catch (RpcRemoteException remoteError) {
                if (isRetryable(remoteError)) {
                    endpointStats.endFailure(elapsed, resilienceOptions);
                    retryOrComplete(
                            reference,
                            method,
                            encodedArguments,
                            deadlineNanos,
                            attempt,
                            result,
                            remoteError);
                } else {
                    endpointStats.endSuccess(elapsed);
                    method.circuitBreaker().onSuccess();
                    result.completeExceptionally(remoteError);
                }
            } catch (Throwable error) {
                endpointStats.endFailure(elapsed, resilienceOptions);
                retryOrComplete(
                        reference,
                        method,
                        encodedArguments,
                        deadlineNanos,
                        attempt,
                        result,
                        error);
            }
        });
    }

    private void retryOrComplete(
            ClientReference reference,
            ClientMethodBinding method,
            byte[] encodedArguments,
            long deadlineNanos,
            int attempt,
            CompletableFuture<Object> result,
            Throwable failure) {
        if (result.isDone()) {
            return;
        }
        boolean retry = method.idempotent()
                && isRetryable(failure)
                && attempt < resilienceOptions.maxAttempts()
                && retryBudget.tryAcquireRetry();
        if (!retry) {
            method.circuitBreaker().onFailure();
            result.completeExceptionally(failure);
            return;
        }

        long remainingNanos = deadlineNanos - System.nanoTime();
        long delayMillis = retryDelayMillis(attempt);
        if (remainingNanos
                <= TimeUnit.MILLISECONDS.toNanos(delayMillis)) {
            method.circuitBreaker().onFailure();
            result.completeExceptionally(failure);
            return;
        }
        CompletableFuture.delayedExecutor(
                        delayMillis,
                        TimeUnit.MILLISECONDS)
                .execute(() -> attempt(
                        reference,
                        method,
                        encodedArguments,
                        deadlineNanos,
                        attempt + 1,
                        result));
    }

    private long retryDelayMillis(int attempt) {
        long base = resilienceOptions.retryBaseBackoff().toMillis();
        long max = resilienceOptions.retryMaxBackoff().toMillis();
        if (base == 0L || max == 0L) {
            return 0L;
        }
        int shift = Math.min(Math.max(attempt - 1, 0), 20);
        long ceiling = base > (Long.MAX_VALUE >> shift)
                ? max
                : Math.min(max, base << shift);
        return ceiling <= 1L
                ? ceiling
                : ThreadLocalRandom.current().nextLong(ceiling + 1L);
    }

    private static boolean isRetryable(Throwable error) {
        if (error instanceof RpcUnavailableException
                || error instanceof RpcOverloadedException) {
            return true;
        }
        if (error instanceof RpcRemoteException remote) {
            return remote.status() == RpcStatus.UNAVAILABLE
                    || remote.status() == RpcStatus.OVERLOADED
                    || remote.status() == RpcStatus.INTERNAL_ERROR;
        }
        return false;
    }

    private static Throwable unwrap(Throwable error) {
        if (error instanceof CompletionException
                && error.getCause() != null) {
            return error.getCause();
        }
        return error;
    }

    private EndpointStats endpointStats(
            ServiceInstance instance) {
        return stats.computeIfAbsent(
                instance.endpoint(),
                ignored -> new EndpointStats());
    }

    private static Object decodeResponse(
            ClientMethodBinding method,
            byte[] rawResponse) {
        RpcFrameView response = RpcProtocolCodec.view(rawResponse);
        if (response.status() != RpcStatus.OK) {
            var remoteError = RpcErrorCodec.decode(
                    response.bytes(),
                    response.payloadOffset(),
                    response.payloadLength());
            throw new RpcRemoteException(
                    response.status(),
                    remoteError.errorType(),
                    remoteError.message());
        }
        if (response.codec() != method.codec().codecId()) {
            throw new RpcException(
                    "RPC response codec does not match bound method codec");
        }
        return method.codec().decodeResult(
                response.bytes(),
                response.payloadOffset(),
                response.payloadLength());
    }

    @Override
    public void close() {
        directories.values().forEach(ServiceDirectory::close);
        transport.close();
    }

    private record ClientReference(
            ServiceKey key,
            int serviceId,
            ServiceDirectory directory,
            Map<Method, ClientMethodBinding> methods,
            Map<Integer, ClientMethodBinding> methodsById) {

        private static ClientReference create(
                ServiceKey key,
                Class<?> api,
                ServiceDirectory directory,
                RpcCodecRegistry codecs,
                byte codecId,
                RpcClientResilienceOptions resilienceOptions) {
            Map<Method, ClientMethodBinding> methods = new HashMap<>();
            Map<Integer, ClientMethodBinding> methodsById = new HashMap<>();
            for (Method method : api.getMethods()) {
                RpcMethodDescriptor descriptor = RpcMethodDescriptor.from(key, method);
                ClientMethodBinding binding = new ClientMethodBinding(
                        descriptor.methodId(),
                        codecs.bind(descriptor, codecId),
                        method.isAnnotationPresent(PeachRpcIdempotent.class),
                        new RpcCircuitBreaker(
                                resilienceOptions.circuitConsecutiveFailureThreshold(),
                                resilienceOptions.circuitOpenDuration()));
                ClientMethodBinding collision = methodsById.putIfAbsent(
                        descriptor.methodId(),
                        binding);
                if (collision != null) {
                    throw new IllegalStateException(
                            "Method id collision in "
                                    + api.getName()
                                    + ": "
                                    + descriptor.methodId());
                }
                methods.put(method, binding);
            }
            return new ClientReference(
                    key,
                    io.peach.rpc.api.RpcIds.serviceId(key),
                    directory,
                    Map.copyOf(methods),
                    Map.copyOf(methodsById));
        }

        private ClientMethodBinding require(Method method) {
            ClientMethodBinding binding = methods.get(method);
            if (binding == null) {
                throw new IllegalStateException(
                        "Method is not part of RPC service contract: " + method);
            }
            return binding;
        }

        private ClientMethodBinding require(int methodId) {
            ClientMethodBinding binding = methodsById.get(methodId);
            if (binding == null) {
                throw new IllegalStateException(
                        "Method id is not part of RPC service contract: " + methodId);
            }
            return binding;
        }
    }

    private record ClientMethodBinding(
            int methodId,
            RpcMethodCodec codec,
            boolean idempotent,
            RpcCircuitBreaker circuitBreaker) {
    }

    private final class GeneratedInvocation implements RpcGeneratedInvocation {
        private final ClientReference reference;

        private GeneratedInvocation(ClientReference reference) {
            this.reference = reference;
        }

        @Override
        public CompletionStage<Object> invoke0(int methodId) {
            return PeachRpcClient.this.invoke0(
                    reference,
                    reference.require(methodId));
        }

        @Override
        public CompletionStage<Object> invoke1(
                int methodId,
                Object argument0) {
            return PeachRpcClient.this.invoke1(
                    reference,
                    reference.require(methodId),
                    argument0);
        }

        @Override
        public CompletionStage<Object> invoke2(
                int methodId,
                Object argument0,
                Object argument1) {
            return PeachRpcClient.this.invoke2(
                    reference,
                    reference.require(methodId),
                    argument0,
                    argument1);
        }

        @Override
        public CompletionStage<Object> invoke3(
                int methodId,
                Object argument0,
                Object argument1,
                Object argument2) {
            return PeachRpcClient.this.invoke3(
                    reference,
                    reference.require(methodId),
                    argument0,
                    argument1,
                    argument2);
        }

        @Override
        public CompletionStage<Object> invoke4(
                int methodId,
                Object argument0,
                Object argument1,
                Object argument2,
                Object argument3) {
            return PeachRpcClient.this.invoke4(
                    reference,
                    reference.require(methodId),
                    argument0,
                    argument1,
                    argument2,
                    argument3);
        }

        @Override
        public CompletionStage<Object> invokeN(
                int methodId,
                Object[] arguments) {
            return PeachRpcClient.this.invokeN(
                    reference,
                    reference.require(methodId),
                    arguments);
        }
    }

    /** Consumer 运行时 Builder。 */
    public static final class Builder {
        private ServiceDiscovery discovery;
        private RpcTransportClient transport;
        private RpcCodecRegistry codecs;
        private LoadBalancer loadBalancer;
        private ProxyFactory proxyFactory;
        private Duration timeout = Duration.ofSeconds(3);
        private RpcClientResilienceOptions resilienceOptions =
                RpcClientResilienceOptions.DEFAULT;

        /** 创建 Consumer Builder。 */
        public Builder() {
        }

        /**
         * 设置服务发现控制面。
         *
         * @param value 服务发现控制面
         * @return Consumer Builder
         */
        public Builder serviceDiscovery(ServiceDiscovery value) {
            this.discovery = value;
            return this;
        }

        /**
         * 设置 Transport Client。
         *
         * @param value Transport Client
         * @return Consumer Builder
         */
        public Builder transportClient(RpcTransportClient value) {
            this.transport = value;
            return this;
        }

        /**
         * 设置 Codec 注册表。
         *
         * @param value Codec 注册表
         * @return Consumer Builder
         */
        public Builder codecRegistry(RpcCodecRegistry value) {
            this.codecs = value;
            return this;
        }

        /**
         * 设置负载均衡器。
         *
         * @param value 负载均衡器
         * @return Consumer Builder
         */
        public Builder loadBalancer(LoadBalancer value) {
            this.loadBalancer = value;
            return this;
        }

        /**
         * 设置代理工厂。
         *
         * @param value 代理工厂
         * @return Consumer Builder
         */
        public Builder proxyFactory(ProxyFactory value) {
            this.proxyFactory = value;
            return this;
        }

        /**
         * 设置默认 RPC 调用超时时间。
         *
         * @param value 默认 RPC 调用超时时间
         * @return Consumer Builder
         */
        public Builder timeout(Duration value) {
            if (value == null || value.isZero() || value.isNegative()) {
                throw new IllegalArgumentException("timeout must be positive");
            }
            this.timeout = value;
            return this;
        }

        /**
         * 设置 Consumer 容错参数。
         *
         * @param value 容错参数
         * @return Consumer Builder
         */
        public Builder resilienceOptions(RpcClientResilienceOptions value) {
            this.resilienceOptions = Objects.requireNonNull(
                    value,
                    "resilienceOptions");
            return this;
        }

        /**
         * 创建 Consumer 运行时。
         *
         * @return Consumer 运行时
         */
        public PeachRpcClient build() {
            return new PeachRpcClient(this);
        }
    }
}
