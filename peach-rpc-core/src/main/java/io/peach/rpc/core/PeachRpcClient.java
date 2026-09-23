package io.peach.rpc.core;

import io.peach.rpc.api.RpcEndpoint;
import io.peach.rpc.api.RpcException;
import io.peach.rpc.api.RpcMethodDescriptor;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Peach RPC Consumer 运行时。 */
public final class PeachRpcClient implements AutoCloseable {
    private final ServiceDiscovery discovery;
    private final RpcTransportClient transport;
    private final RpcCodecRegistry codecs;
    private final byte defaultCodecId;
    private final LoadBalancer loadBalancer;
    private final ProxyFactory proxyFactory;
    private final Duration timeout;
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
                defaultCodecId);

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
        ServiceInstance[] instances =
                reference.directory().snapshot();
        if (instances.length == 0) {
            return CompletableFuture.failedFuture(
                    new RpcUnavailableException(
                            "No available instance for "
                                    + reference.key().canonicalName()));
        }

        ServiceInstance selected =
                loadBalancer.select(instances, loadMetrics);
        if (selected == null) {
            return CompletableFuture.failedFuture(new RpcUnavailableException(
                    "No available instance for " + reference.key().canonicalName()));
        }

        EndpointStats endpointStats = endpointStats(selected);
        long startedAtNanos = System.nanoTime();
        endpointStats.begin();

        long deadlineEpochMillis =
                System.currentTimeMillis() + timeout.toMillis();
        byte[] request = RpcProtocolCodec.encodeRequest(
                method.codec().codecId(),
                reference.serviceId(),
                method.methodId(),
                deadlineEpochMillis,
                encodedArguments);

        CompletableFuture<Object> result = new CompletableFuture<>();
        transport.request(
                        selected.endpoint(),
                        request,
                        timeout)
                .whenComplete((rawResponse, transportError) -> {
                    endpointStats.end(System.nanoTime() - startedAtNanos);
                    if (transportError != null) {
                        result.completeExceptionally(transportError);
                        return;
                    }
                    completeResponse(method, result, rawResponse);
                });
        return result;
    }

    private EndpointStats endpointStats(
            ServiceInstance instance) {
        return stats.computeIfAbsent(
                instance.endpoint(),
                ignored -> new EndpointStats());
    }

    private static void completeResponse(
            ClientMethodBinding method,
            CompletableFuture<Object> result,
            byte[] rawResponse) {
        try {
            RpcFrameView response = RpcProtocolCodec.view(rawResponse);
            if (response.status() != RpcStatus.OK) {
                var remoteError = RpcErrorCodec.decode(
                        response.bytes(),
                        response.payloadOffset(),
                        response.payloadLength());
                result.completeExceptionally(new RpcException(remoteError.message()));
                return;
            }
            if (response.codec() != method.codec().codecId()) {
                throw new RpcException(
                        "RPC response codec does not match bound method codec");
            }
            result.complete(method.codec().decodeResult(
                    response.bytes(),
                    response.payloadOffset(),
                    response.payloadLength()));
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
            Map<Method, ClientMethodBinding> methods,
            Map<Integer, ClientMethodBinding> methodsById) {

        private static ClientReference create(
                ServiceKey key,
                Class<?> api,
                ServiceDirectory directory,
                RpcCodecRegistry codecs,
                byte codecId) {
            Map<Method, ClientMethodBinding> methods = new HashMap<>();
            Map<Integer, ClientMethodBinding> methodsById = new HashMap<>();
            for (Method method : api.getMethods()) {
                RpcMethodDescriptor descriptor = RpcMethodDescriptor.from(key, method);
                ClientMethodBinding binding = new ClientMethodBinding(
                        descriptor.methodId(),
                        codecs.bind(descriptor, codecId));
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
            RpcMethodCodec codec) {
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
         * 创建 Consumer 运行时。
         *
         * @return Consumer 运行时
         */
        public PeachRpcClient build() {
            return new PeachRpcClient(this);
        }
    }
}
