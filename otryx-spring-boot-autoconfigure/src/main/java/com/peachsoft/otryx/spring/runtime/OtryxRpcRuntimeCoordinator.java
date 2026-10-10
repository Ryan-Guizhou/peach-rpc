package io.peach.rpc.spring.runtime;

import io.peach.rpc.api.RpcEndpoint;
import io.peach.rpc.codec.RpcCodecRegistry;
import io.peach.rpc.core.PeachRpcClient;
import io.peach.rpc.core.PeachRpcServer;
import io.peach.rpc.core.RpcProviderAdmissionOptions;
import io.peach.rpc.core.RpcClientResilienceOptions;
import io.peach.rpc.core.RpcProviderExecutionOptions;
import io.peach.rpc.loadbalance.LoadBalancer;
import io.peach.rpc.observability.RpcMetadataPropagator;
import io.peach.rpc.observability.RpcObserver;
import io.peach.rpc.observability.RpcTracingBridge;
import io.peach.rpc.proxy.ProxyFactory;
import io.peach.rpc.registry.Registry;
import io.peach.rpc.spring.autoconfigure.PeachRpcProperties;
import io.peach.rpc.transport.RpcTransportFactory;
import io.peach.rpc.transport.RpcTransportOptions;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 按需创建并持有 Peach RPC Consumer / Provider 运行时。
 */
public final class PeachRpcRuntimeCoordinator implements AutoCloseable {

    private final Registry registry;
    private final RpcCodecRegistry codecRegistry;
    private final RpcTransportFactory transportFactory;
    private final RpcTransportOptions transportOptions;
    private final LoadBalancer loadBalancer;
    private final ProxyFactory proxyFactory;
    private final RpcClientResilienceOptions resilienceOptions;
    private final RpcProviderExecutionOptions executionOptions;
    private final ObjectProvider<RpcObserver> observerProvider;
    private final ObjectProvider<RpcMetadataPropagator>
            metadataPropagatorProvider;
    private final ObjectProvider<RpcTracingBridge>
            tracingBridgeProvider;
    private final PeachRpcProperties properties;
    private volatile PeachRpcClient client;
    private volatile PeachRpcServer server;
    private volatile boolean providerAutoStart;

    /**
     * 创建运行时协调器。
     *
     * @param registry 注册中心
     * @param codecRegistry Codec 注册表
     * @param transportFactory Transport 工厂
     * @param transportOptions Transport 配置
     * @param loadBalancer Consumer 负载均衡器
     * @param proxyFactory Consumer 代理工厂
     * @param resilienceOptions Consumer 容错参数
     * @param executionOptions Provider 执行资源参数
     * @param observerProvider 可观测性 Observer 提供器
     * @param metadataPropagatorProvider Metadata 传播器提供器
     * @param tracingBridgeProvider 分布式 Trace Bridge 提供器
     * @param properties Peach RPC 配置
     */
    public PeachRpcRuntimeCoordinator(
            Registry registry,
            RpcCodecRegistry codecRegistry,
            RpcTransportFactory transportFactory,
            RpcTransportOptions transportOptions,
            LoadBalancer loadBalancer,
            ProxyFactory proxyFactory,
            RpcClientResilienceOptions resilienceOptions,
            RpcProviderExecutionOptions executionOptions,
            ObjectProvider<RpcObserver> observerProvider,
            ObjectProvider<RpcMetadataPropagator> metadataPropagatorProvider,
            ObjectProvider<RpcTracingBridge> tracingBridgeProvider,
            PeachRpcProperties properties) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.codecRegistry = Objects.requireNonNull(codecRegistry, "codecRegistry");
        this.transportFactory = Objects.requireNonNull(transportFactory, "transportFactory");
        this.transportOptions = Objects.requireNonNull(transportOptions, "transportOptions");
        this.loadBalancer = Objects.requireNonNull(loadBalancer, "loadBalancer");
        this.proxyFactory = Objects.requireNonNull(proxyFactory, "proxyFactory");
        this.resilienceOptions = Objects.requireNonNull(
                resilienceOptions,
                "resilienceOptions");
        this.executionOptions = Objects.requireNonNull(
                executionOptions,
                "executionOptions");
        this.observerProvider = Objects.requireNonNull(
                observerProvider,
                "observerProvider");
        this.metadataPropagatorProvider =
                Objects.requireNonNull(
                        metadataPropagatorProvider,
                        "metadataPropagatorProvider");
        this.tracingBridgeProvider =
                Objects.requireNonNull(
                        tracingBridgeProvider,
                        "tracingBridgeProvider");
        this.properties = Objects.requireNonNull(
                properties,
                "properties");
    }

    /**
     * 获取或创建 Consumer 运行时。
     *
     * @return Consumer 运行时
     */
    public PeachRpcClient client() {
        if (!properties.getClient().isEnabled()) {
            throw new IllegalStateException(
                    "Peach RPC Consumer capability is disabled by peach.rpc.client.enabled=false");
        }
        PeachRpcClient current = client;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (client == null) {
                client = createClient();
            }
            return client;
        }
    }

    /**
     * 获取或创建 Provider 运行时。
     *
     * @return Provider 运行时
     */
    public PeachRpcServer server() {
        if (!properties.getServer().isEnabled()) {
            throw new IllegalStateException(
                    "Peach RPC Provider capability is disabled by peach.rpc.server.enabled=false");
        }
        PeachRpcServer current = server;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (server == null) {
                server = createServer();
            }
            return server;
        }
    }

    /**
     * 为注解服务获取 Provider，并标记为 Spring 生命周期自动启动。
     *
     * @return Provider 运行时
     */
    public PeachRpcServer serverForServiceExport() {
        providerAutoStart = true;
        return server();
    }

    /**
     * 返回已经创建的 Consumer。
     *
     * @return Consumer
     */
    public Optional<PeachRpcClient> clientIfCreated() {
        return Optional.ofNullable(client);
    }

    /**
     * 返回已经创建的 Provider。
     *
     * @return Provider
     */
    public Optional<PeachRpcServer> serverIfCreated() {
        return Optional.ofNullable(server);
    }

    /**
     * 返回需要由 Spring 生命周期自动启动的 Provider。
     *
     * @return 自动启动 Provider
     */
    public Optional<PeachRpcServer> autoStartServerIfCreated() {
        return providerAutoStart
                ? Optional.ofNullable(server)
                : Optional.empty();
    }

    private PeachRpcClient createClient() {
        PeachRpcProperties.Client clientProperties = properties.getClient();
        return PeachRpcClient.builder()
                .serviceDiscovery(registry)
                .codecRegistry(codecRegistry)
                .transportClient(transportFactory.createClient(
                        transportOptions.withObserver(observer())))
                .loadBalancer(loadBalancer)
                .proxyFactory(proxyFactory)
                .timeout(clientProperties.getTimeout())
                .resilienceOptions(resilienceOptions)
                .observer(observer())
                .metadataPropagator(metadataPropagator())
                .tracingBridge(tracingBridge())
                .build();
    }

    private PeachRpcServer createServer() {
        PeachRpcProperties.Server serverProperties = properties.getServer();
        return PeachRpcServer.builder()
                .serviceRegistrar(registry.registrar().orElseThrow(() ->
                        new IllegalStateException(
                                "Configured RPC registry does not support provider registration")))
                .codecRegistry(codecRegistry)
                .transportServer(transportFactory.createServer(
                        transportOptions.withObserver(observer())))
                .bindEndpoint(new RpcEndpoint(
                        serverProperties.getHost(),
                        serverProperties.getPort()))
                .advertisedHost(serverProperties.getAdvertisedHost())
                .advertisedPort(serverProperties.getAdvertisedPort())
                .maxConcurrent(serverProperties.getMaxConcurrent())
                .admissionOptions(providerAdmissionOptions(
                        serverProperties.getAdmission()))
                .drainTimeout(serverProperties.getDrainTimeout())
                .controlPlaneTimeout(serverProperties.getControlPlaneTimeout())
                .executionOptions(executionOptions)
                .observer(observer())
                .metadataPropagator(metadataPropagator())
                .tracingBridge(tracingBridge())
                .build();
    }

    private static RpcProviderAdmissionOptions providerAdmissionOptions(
            PeachRpcProperties.Admission policy) {
        return new RpcProviderAdmissionOptions(
                policy.getMaxInflightBytes(),
                policy.getMaxConcurrentPerService(),
                policy.getMaxConcurrentPerMethod(),
                policy.getMaxInflightBytesPerService(),
                policy.getMaxInflightBytesPerMethod());
    }

    private RpcObserver observer() {
        return RpcObserver.composite(
                observerProvider.orderedStream().toList());
    }

    private RpcMetadataPropagator metadataPropagator() {
        return RpcMetadataPropagator.composite(
                metadataPropagatorProvider
                        .orderedStream()
                        .toList());
    }

    private RpcTracingBridge tracingBridge() {
        return tracingBridgeProvider
                .orderedStream()
                .findFirst()
                .orElseGet(RpcTracingBridge::noop);
    }

    @Override
    public void close() {
        PeachRpcClient currentClient = client;
        if (currentClient != null) {
            currentClient.close();
        }
        PeachRpcServer currentServer = server;
        if (currentServer != null) {
            currentServer.close();
        }
    }
}
