package io.peach.rpc.core;

import io.peach.rpc.api.RpcEndpoint;
import io.peach.rpc.api.RpcException;
import io.peach.rpc.api.RpcIds;
import io.peach.rpc.api.RpcStatus;
import io.peach.rpc.api.RpcUnavailableException;
import io.peach.rpc.api.ServiceInstance;
import io.peach.rpc.api.ServiceKey;
import io.peach.rpc.codec.RpcCodec;
import io.peach.rpc.codec.RpcCodecRegistry;
import io.peach.rpc.loadbalance.LoadBalanceContext;
import io.peach.rpc.loadbalance.LoadBalancer;
import io.peach.rpc.protocol.RpcFrame;
import io.peach.rpc.protocol.RpcMessageType;
import io.peach.rpc.protocol.RpcProtocolCodec;
import io.peach.rpc.proxy.ProxyFactory;
import io.peach.rpc.registry.Registry;
import io.peach.rpc.spi.ExtensionLoader;
import io.peach.rpc.transport.RpcTransportClient;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/** Peach RPC Consumer 运行时。 */
public final class PeachRpcClient implements AutoCloseable {
    private final Registry registry;
    private final RpcTransportClient transport;
    private final RpcCodecRegistry codecs;
    private final RpcCodec defaultCodec;
    private final LoadBalancer loadBalancer;
    private final ProxyFactory proxyFactory;
    private final Duration timeout;
    private final AtomicLong requestIds = new AtomicLong();
    private final ConcurrentMap<ServiceKey, ServiceDirectory> directories = new ConcurrentHashMap<>();
    private final ConcurrentMap<RpcEndpoint, EndpointStats> stats = new ConcurrentHashMap<>();

    private PeachRpcClient(Builder builder) {
        this.registry = Objects.requireNonNull(builder.registry, "registry");
        this.transport = Objects.requireNonNull(builder.transport, "transportClient");
        this.codecs = Objects.requireNonNull(builder.codecs, "codecRegistry");
        this.defaultCodec = codecs.defaultCodec();
        this.loadBalancer = builder.loadBalancer != null
                ? builder.loadBalancer
                : ExtensionLoader.getLoader(LoadBalancer.class).getDefaultExtension();
        this.proxyFactory = builder.proxyFactory != null
                ? builder.proxyFactory
                : ExtensionLoader.getLoader(ProxyFactory.class).getDefaultExtension();
        this.timeout = Objects.requireNonNull(builder.timeout, "timeout");
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
     * <p>服务标识、方法标识和本地目录在此阶段预解析，避免进入单次请求热路径。
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
        ServiceDirectory directory = directories.computeIfAbsent(key, ignored -> new ServiceDirectory(registry, key));
        ClientReference reference = ClientReference.create(key, api, directory);
        return proxyFactory.create(api, (method, args) -> invoke(reference, method, args));
    }

    private CompletionStage<Object> invoke(ClientReference reference, Method method, Object[] args) {
        List<ServiceInstance> instances = reference.directory().snapshot();
        if (instances.isEmpty()) {
            return CompletableFuture.failedFuture(new RpcUnavailableException(
                    "No available instance for " + reference.key().canonicalName()));
        }

        List<LoadBalanceContext> candidates = instances.stream()
                .map(instance -> {
                    EndpointStats endpointStats = stats.computeIfAbsent(
                            instance.endpoint(), ignored -> new EndpointStats());
                    return new LoadBalanceContext(
                            instance, endpointStats.ewma(), endpointStats.inflight());
                })
                .toList();
        ServiceInstance selected = loadBalancer.select(candidates);
        if (selected == null) {
            return CompletableFuture.failedFuture(new RpcUnavailableException(
                    "No available instance for " + reference.key().canonicalName()));
        }

        Integer methodId = reference.methodIds().get(method);
        if (methodId == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Method is not part of RPC service contract: " + method));
        }

        EndpointStats endpointStats = stats.computeIfAbsent(
                selected.endpoint(), ignored -> new EndpointStats());
        long startedAtNanos = System.nanoTime();
        endpointStats.begin();

        long requestId = requestIds.incrementAndGet();
        RpcFrame request = new RpcFrame(
                RpcMessageType.REQUEST,
                defaultCodec.code(),
                RpcStatus.OK,
                requestId,
                reference.serviceId(),
                methodId,
                Map.of("deadlineEpochMillis", Long.toString(System.currentTimeMillis() + timeout.toMillis())),
                defaultCodec.encode(new RpcInvocationPayload(args)));

        CompletableFuture<Object> result = new CompletableFuture<>();
        transport.request(selected.endpoint(), requestId, RpcProtocolCodec.encode(request), timeout)
                .whenComplete((rawResponse, transportError) -> {
                    endpointStats.end(System.nanoTime() - startedAtNanos);
                    if (transportError != null) {
                        result.completeExceptionally(transportError);
                        return;
                    }
                    completeResponse(result, rawResponse);
                });
        return result;
    }

    private void completeResponse(CompletableFuture<Object> result, byte[] rawResponse) {
        try {
            RpcFrame response = RpcProtocolCodec.decode(rawResponse);
            RpcResultPayload payload = codecs.require(response.codec())
                    .decode(response.payload(), RpcResultPayload.class);
            if (response.status() != RpcStatus.OK || !payload.success()) {
                result.completeExceptionally(new RpcException(payload.errorMessage()));
            } else {
                result.complete(payload.value());
            }
        } catch (Throwable error) {
            result.completeExceptionally(error);
        }
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
            Map<Method, Integer> methodIds) {
        private static ClientReference create(
                ServiceKey key, Class<?> api, ServiceDirectory directory) {
            Map<Method, Integer> methodIds = new HashMap<>();
            Map<Integer, Method> collisionGuard = new HashMap<>();
            for (Method method : api.getMethods()) {
                int methodId = RpcIds.methodId(method);
                Method collision = collisionGuard.putIfAbsent(methodId, method);
                if (collision != null) {
                    throw new IllegalStateException(
                            "Method id collision in " + api.getName() + ": " + collision + " vs " + method);
                }
                methodIds.put(method, methodId);
            }
            return new ClientReference(
                    key, RpcIds.serviceId(key), directory, Map.copyOf(methodIds));
        }
    }

    /**
     * Consumer 运行时 Builder。
     */
    public static final class Builder {
        private Registry registry;
        private RpcTransportClient transport;
        private RpcCodecRegistry codecs;
        private LoadBalancer loadBalancer;
        private ProxyFactory proxyFactory;
        private Duration timeout = Duration.ofSeconds(3);

        /**
         * 创建 Consumer Builder。
         */
        public Builder() {
        }


        /**
         * 设置注册中心。
         *
         * @param value 注册中心
         * @return Consumer Builder
         */
        public Builder registry(Registry value) {
            this.registry = value;
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
         * 创建 Consumer 运行时。
         *
         * @return Consumer 运行时
         */
        public PeachRpcClient build() {
            return new PeachRpcClient(this);
        }
    }
}
