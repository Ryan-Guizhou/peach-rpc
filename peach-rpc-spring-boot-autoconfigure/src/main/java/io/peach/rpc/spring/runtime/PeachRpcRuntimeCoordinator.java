package io.peach.rpc.spring.runtime;

import io.peach.rpc.api.RpcEndpoint;
import io.peach.rpc.codec.RpcCodecRegistry;
import io.peach.rpc.core.PeachRpcClient;
import io.peach.rpc.core.PeachRpcServer;
import io.peach.rpc.core.RpcClientResilienceOptions;
import io.peach.rpc.core.RpcProviderExecutionOptions;
import io.peach.rpc.loadbalance.LoadBalancer;
import io.peach.rpc.observability.RpcObserver;
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
    private final PeachRpcProperties properties;
    private volatile PeachRpcClient client;
    private volatile PeachRpcServer server;

    /**
     * 创建运行时协调器。
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
        this.properties = Objects.requireNonNull(properties, "properties");
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

    private PeachRpcClient createClient() {
        PeachRpcProperties.Client clientProperties = properties.getClient();
        return PeachRpcClient.builder()
                .serviceDiscovery(registry)
                .codecRegistry(codecRegistry)
                .transportClient(transportFactory.createClient(transportOptions))
                .loadBalancer(loadBalancer)
                .proxyFactory(proxyFactory)
                .timeout(clientProperties.getTimeout())
                .resilienceOptions(resilienceOptions)
                .observer(observer())
                .build();
    }

    private PeachRpcServer createServer() {
        PeachRpcProperties.Server serverProperties = properties.getServer();
        return PeachRpcServer.builder()
                .serviceRegistrar(registry.registrar().orElseThrow(() ->
                        new IllegalStateException(
                                "Configured RPC registry does not support provider registration")))
                .codecRegistry(codecRegistry)
                .transportServer(transportFactory.createServer(transportOptions))
                .bindEndpoint(new RpcEndpoint(
                        serverProperties.getHost(),
                        serverProperties.getPort()))
                .advertisedHost(serverProperties.getAdvertisedHost())
                .advertisedPort(serverProperties.getAdvertisedPort())
                .maxConcurrent(serverProperties.getMaxConcurrent())
                .drainTimeout(serverProperties.getDrainTimeout())
                .controlPlaneTimeout(serverProperties.getControlPlaneTimeout())
                .executionOptions(executionOptions)
                .observer(observer())
                .build();
    }

    private RpcObserver observer() {
        return RpcObserver.composite(
                observerProvider.orderedStream().toList());
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
