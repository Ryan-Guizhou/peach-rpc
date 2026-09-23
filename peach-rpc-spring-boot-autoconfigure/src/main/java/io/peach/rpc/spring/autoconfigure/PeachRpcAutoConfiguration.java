package io.peach.rpc.spring.autoconfigure;

import io.peach.rpc.api.RpcEndpoint;
import io.peach.rpc.codec.RpcCodecRegistry;
import io.peach.rpc.core.PeachRpcClient;
import io.peach.rpc.core.PeachRpcServer;
import io.peach.rpc.loadbalance.LoadBalancer;
import io.peach.rpc.proxy.ProxyFactory;
import io.peach.rpc.registry.Registry;
import io.peach.rpc.registry.RegistryFactory;
import io.peach.rpc.registry.RegistryOptions;
import io.peach.rpc.spi.ExtensionLoader;
import io.peach.rpc.spring.lifecycle.PeachRpcServerLifecycle;
import io.peach.rpc.spring.processor.PeachRpcReferenceBeanPostProcessor;
import io.peach.rpc.spring.processor.PeachRpcServiceBeanPostProcessor;
import io.peach.rpc.transport.RpcTransportFactory;
import io.peach.rpc.transport.RpcTransportOptions;
import java.util.Map;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Peach RPC Spring Boot 自动配置。
 */
@AutoConfiguration
@ConditionalOnClass(PeachRpcClient.class)
@ConditionalOnProperty(prefix = "peach.rpc", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(PeachRpcProperties.class)
public class PeachRpcAutoConfiguration {

    /**
     * 创建 Peach RPC 自动配置。
     */
    public PeachRpcAutoConfiguration() {
    }

    /**
     * 创建注册中心实例。
     *
     * @param properties Peach RPC 配置
     * @return 注册中心实例
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public Registry peachRpcRegistry(PeachRpcProperties properties) {
        PeachRpcProperties.Registry registry = properties.getRegistry();
        RegistryFactory factory = ExtensionLoader.getLoader(RegistryFactory.class)
                .getExtension(registry.getType());
        return factory.create(RegistryOptions.fromCsv(
                registry.getEndpoints(),
                registry.getNamespace(),
                Map.of(
                        "leaseTtlSeconds",
                        Long.toString(registry.getLeaseTtlSeconds()))));
    }

    /**
     * 创建 Codec 注册表。
     *
     * @return Codec 注册表
     */
    @Bean
    @ConditionalOnMissingBean
    public RpcCodecRegistry peachRpcCodecRegistry() {
        return RpcCodecRegistry.fromSpi();
    }

    /**
     * 创建 Transport 工厂。
     *
     * @param properties Peach RPC 配置
     * @return Transport 工厂
     */
    @Bean
    @ConditionalOnMissingBean
    public RpcTransportFactory peachRpcTransportFactory(PeachRpcProperties properties) {
        return ExtensionLoader.getLoader(RpcTransportFactory.class)
                .getExtension(properties.getTransport().getType());
    }

    /**
     * 创建 Transport 运行参数。
     *
     * @param properties Peach RPC 配置
     * @return Transport 运行参数
     */
    @Bean
    @ConditionalOnMissingBean
    public RpcTransportOptions peachRpcTransportOptions(PeachRpcProperties properties) {
        PeachRpcProperties.Transport transport = properties.getTransport();
        return new RpcTransportOptions(
                transport.getMaxInflightPerConnection(),
                transport.getMaxFrameBytes(),
                transport.getMaxWriteQueueBytes(),
                transport.getConnectTimeout());
    }

    /**
     * 创建 Consumer 负载均衡器。
     *
     * @param properties Peach RPC 配置
     * @return 负载均衡器
     */
    @Bean
    @ConditionalOnMissingBean
    public LoadBalancer peachRpcLoadBalancer(PeachRpcProperties properties) {
        return ExtensionLoader.getLoader(LoadBalancer.class)
                .getExtension(properties.getClient().getLoadBalancer());
    }

    /**
     * 创建 Consumer 代理工厂。
     *
     * @param properties Peach RPC 配置
     * @return 代理工厂
     */
    @Bean
    @ConditionalOnMissingBean
    public ProxyFactory peachRpcProxyFactory(PeachRpcProperties properties) {
        return ExtensionLoader.getLoader(ProxyFactory.class)
                .getExtension(properties.getClient().getProxy());
    }

    /**
     * 创建 Peach RPC Consumer 运行时。
     *
     * @param registry 注册中心
     * @param codecRegistry Codec 注册表
     * @param transportFactory Transport 工厂
     * @param transportOptions Transport 配置
     * @param loadBalancer 负载均衡器
     * @param proxyFactory 代理工厂
     * @param properties Peach RPC 配置
     * @return Consumer 运行时
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "peach.rpc.client", name = "enabled", havingValue = "true", matchIfMissing = true)
    public PeachRpcClient peachRpcClient(
            Registry registry,
            RpcCodecRegistry codecRegistry,
            RpcTransportFactory transportFactory,
            RpcTransportOptions transportOptions,
            LoadBalancer loadBalancer,
            ProxyFactory proxyFactory,
            PeachRpcProperties properties) {
        return PeachRpcClient.builder()
                .serviceDiscovery(registry)
                .codecRegistry(codecRegistry)
                .transportClient(transportFactory.createClient(transportOptions))
                .loadBalancer(loadBalancer)
                .proxyFactory(proxyFactory)
                .timeout(properties.getClient().getTimeout())
                .build();
    }

    /**
     * 创建 Consumer 引用注入处理器。
     *
     * @param clientProvider Consumer 运行时延迟提供器
     * @return Consumer 引用注入处理器
     */
    @Bean
    @ConditionalOnBean(PeachRpcClient.class)
    @ConditionalOnMissingBean
    public static PeachRpcReferenceBeanPostProcessor peachRpcReferenceBeanPostProcessor(
            ObjectProvider<PeachRpcClient> clientProvider) {
        return new PeachRpcReferenceBeanPostProcessor(clientProvider);
    }

    /**
     * 创建 Peach RPC Provider 运行时。
     *
     * @param registry 注册中心
     * @param codecRegistry Codec 注册表
     * @param transportFactory Transport 工厂
     * @param transportOptions Transport 配置
     * @param properties Peach RPC 配置
     * @return Provider 运行时
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "peach.rpc.server", name = "enabled", havingValue = "true")
    public PeachRpcServer peachRpcServer(
            Registry registry,
            RpcCodecRegistry codecRegistry,
            RpcTransportFactory transportFactory,
            RpcTransportOptions transportOptions,
            PeachRpcProperties properties) {
        PeachRpcProperties.Server server = properties.getServer();
        return PeachRpcServer.builder()
                .serviceRegistrar(registry.registrar().orElseThrow(() ->
                        new IllegalStateException("Configured RPC registry does not support provider registration")))
                .codecRegistry(codecRegistry)
                .transportServer(transportFactory.createServer(transportOptions))
                .bindEndpoint(new RpcEndpoint(server.getHost(), server.getPort()))
                .maxConcurrent(server.getMaxConcurrent())
                .build();
    }

    /**
     * 创建 Provider 服务导出处理器。
     *
     * @param serverProvider Provider 运行时延迟提供器
     * @return Provider 服务导出处理器
     */
    @Bean
    @ConditionalOnBean(PeachRpcServer.class)
    @ConditionalOnMissingBean
    public static PeachRpcServiceBeanPostProcessor peachRpcServiceBeanPostProcessor(
            ObjectProvider<PeachRpcServer> serverProvider) {
        return new PeachRpcServiceBeanPostProcessor(serverProvider);
    }

    /**
     * 创建 Provider 生命周期适配器。
     *
     * @param server Provider 运行时
     * @return Provider 生命周期适配器
     */
    @Bean
    @ConditionalOnBean(PeachRpcServer.class)
    @ConditionalOnMissingBean
    public PeachRpcServerLifecycle peachRpcServerLifecycle(PeachRpcServer server) {
        return new PeachRpcServerLifecycle(server);
    }
}
