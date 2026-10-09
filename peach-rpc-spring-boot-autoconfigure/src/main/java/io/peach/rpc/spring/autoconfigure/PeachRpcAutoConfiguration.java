package io.peach.rpc.spring.autoconfigure;

import io.peach.rpc.codec.RpcCodecRegistry;
import io.peach.rpc.codec.fory.ForyRpcCodec;
import io.peach.rpc.codec.fory.ForyRpcSecurityOptions;
import io.peach.rpc.core.PeachRpcClient;
import io.peach.rpc.core.PeachRpcServer;
import io.peach.rpc.core.RpcClientResilienceOptions;
import io.peach.rpc.core.RpcProviderExecutionOptions;
import io.peach.rpc.loadbalance.LoadBalancer;
import io.peach.rpc.observability.RpcMetadataPropagator;
import io.peach.rpc.observability.RpcObserver;
import io.peach.rpc.observability.RpcTracingBridge;
import io.peach.rpc.proxy.ProxyFactory;
import io.peach.rpc.registry.Registry;
import io.peach.rpc.registry.RegistryFactory;
import io.peach.rpc.registry.RegistryOptions;
import io.peach.rpc.spi.ExtensionLoader;
import io.peach.rpc.spring.lifecycle.PeachRpcServerLifecycle;
import io.peach.rpc.spring.processor.PeachRpcReferenceBeanPostProcessor;
import io.peach.rpc.spring.processor.PeachRpcServiceBeanDefinitionValidator;
import io.peach.rpc.spring.processor.PeachRpcServiceBeanPostProcessor;
import io.peach.rpc.spring.runtime.PeachRpcRuntimeCoordinator;
import io.peach.rpc.transport.RpcTransportFactory;
import io.peach.rpc.transport.RpcTransportOptions;
import io.peach.rpc.transport.RpcTransportSecurityOptions;
import java.util.Map;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;

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
     * @param observerProvider Registry 控制面 Observer 提供器
     * @return 注册中心实例
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public Registry peachRpcRegistry(
            PeachRpcProperties properties,
            ObjectProvider<RpcObserver> observerProvider) {
        PeachRpcProperties.Registry registry = properties.getRegistry();
        RegistryFactory factory = requireExtension(
                RegistryFactory.class,
                "peach.rpc.registry.type",
                registry.getType());
        return factory.create(RegistryOptions.fromCsv(
                registry.getEndpoints(),
                registry.getNamespace(),
                Map.of(
                        "leaseTtlSeconds",
                        Long.toString(registry.getLeaseTtlSeconds()),
                        "nacosGroup",
                        registry.getNacos().getGroup(),
                        "nacosCluster",
                        registry.getNacos().getCluster(),
                        "nacosUsername",
                        registry.getNacos().getUsername(),
                        "nacosPassword",
                        registry.getNacos().getPassword()),
                RpcObserver.composite(
                        observerProvider.orderedStream().toList())));
    }

    /**
     * 创建 Codec 注册表。
     *
     * @param properties Codec 安全配置
     * @return Codec 注册表
     */
    @Bean
    @ConditionalOnMissingBean
    public RpcCodecRegistry peachRpcCodecRegistry(PeachRpcProperties properties) {
        PeachRpcProperties.ForySecurity security = properties.getCodec().getFory();
        ForyRpcSecurityOptions options = new ForyRpcSecurityOptions(
                security.getMode(),
                java.util.Set.copyOf(security.getAllowedClassPatterns()),
                security.getMaxDepth(),
                security.getMaxGraphMemoryBytes(),
                security.getMaxPayloadBytes());
        return RpcCodecRegistry.fromSpi()
                .withReplacement(new ForyRpcCodec(options));
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
        return requireExtension(
                RpcTransportFactory.class,
                "peach.rpc.transport.type",
                properties.getTransport().getType());
    }

    /**
     * 创建 Transport 运行参数。
     *
     * @param properties Peach RPC 配置
     * @param codecRegistry Codec 注册表
     * @return Transport 运行参数
     */
    @Bean
    @ConditionalOnMissingBean
    public RpcTransportOptions peachRpcTransportOptions(
            PeachRpcProperties properties,
            RpcCodecRegistry codecRegistry) {
        PeachRpcProperties.Transport transport = properties.getTransport();
        PeachRpcProperties.Security security =
                transport.getSecurity();
        RpcTransportSecurityOptions securityOptions =
                new RpcTransportSecurityOptions(
                        security.getMode(),
                        security.getCertificatePath(),
                        security.getPrivateKeyPath(),
                        security.getTrustCertificatePath(),
                        security.isHostnameVerification(),
                        security.getHandshakeTimeout(),
                        security.getReloadInterval(),
                        security.getExpiryWarningThreshold());
        return new RpcTransportOptions(
                transport.getMaxInflightPerConnection(),
                transport.getMaxFrameBytes(),
                transport.getMaxWriteQueueBytes(),
                transport.getConnectTimeout(),
                transport.getHandshakeTimeout(),
                codecRegistry.supportedCodecIds(),
                transport.getConnectionsPerEndpoint(),
                transport.getHeartbeatInterval(),
                transport.getHeartbeatTimeout(),
                transport.getReconnectBaseBackoff(),
                transport.getReconnectMaxBackoff())
                .withSecurity(securityOptions);
    }

    /**
     * 创建 Consumer 容错参数。
     *
     * @param properties Peach RPC 配置
     * @return Consumer 容错参数
     */
    @Bean
    @ConditionalOnMissingBean
    public RpcClientResilienceOptions peachRpcClientResilienceOptions(
            PeachRpcProperties properties) {
        PeachRpcProperties.Resilience resilience =
                properties.getClient().getResilience();
        return new RpcClientResilienceOptions(
                resilience.getMaxAttempts(),
                resilience.getRetryBudgetRatio(),
                resilience.getRetryBudgetMinRetries(),
                resilience.getRetryBudgetMaxRetries(),
                resilience.getRetryBaseBackoff(),
                resilience.getRetryMaxBackoff(),
                resilience.getOutlierConsecutiveFailureThreshold(),
                resilience.getOutlierEjectionDuration(),
                resilience.getCircuitConsecutiveFailureThreshold(),
                resilience.getCircuitOpenDuration());
    }

    /**
     * 创建 Provider 执行资源参数。
     *
     * @param properties Peach RPC 配置
     * @return Provider 执行资源参数
     */
    @Bean
    @ConditionalOnMissingBean
    public RpcProviderExecutionOptions peachRpcProviderExecutionOptions(
            PeachRpcProperties properties) {
        PeachRpcProperties.Execution execution =
                properties.getServer().getExecution();
        return new RpcProviderExecutionOptions(
                execution.isAllowDirect(),
                execution.getCpuParallelism(),
                execution.getCpuQueueCapacity());
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
        return requireExtension(
                LoadBalancer.class,
                "peach.rpc.client.load-balancer",
                properties.getClient().getLoadBalancer());
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
        return requireExtension(
                ProxyFactory.class,
                "peach.rpc.client.proxy",
                properties.getClient().getProxy());
    }

    /**
     * 使用完整配置键定位缺失或拼写错误的扩展，避免只暴露底层 SPI 异常。
     *
     * @param type SPI 扩展接口
     * @param propertyName 配置键
     * @param name 已配置扩展名称
     * @param <T> SPI 类型
     * @return 匹配的 SPI 扩展实例
     */
    private static <T> T requireExtension(
            Class<T> type,
            String propertyName,
            String name) {
        try {
            return ExtensionLoader.getLoader(type).getExtension(name);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException(
                    "Invalid " + propertyName + "='" + name
                            + "': install the matching Peach RPC adapter "
                            + "or select an available SPI implementation. "
                            + error.getMessage(),
                    error);
        }
    }

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
     * @return 运行时协调器
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public PeachRpcRuntimeCoordinator peachRpcRuntimeCoordinator(
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
        return new PeachRpcRuntimeCoordinator(
                registry,
                codecRegistry,
                transportFactory,
                transportOptions,
                loadBalancer,
                proxyFactory,
                resilienceOptions,
                executionOptions,
                observerProvider,
                metadataPropagatorProvider,
                tracingBridgeProvider,
                properties);
    }

    /**
     * 暴露可选的程序化 Consumer Bean；只有真实注入时才创建运行时。
     *
     * @param coordinator 运行时协调器
     * @return Consumer
     */
    @Bean(destroyMethod = "")
    @Lazy
    @ConditionalOnMissingBean
    public PeachRpcClient peachRpcClient(PeachRpcRuntimeCoordinator coordinator) {
        return coordinator.client();
    }

    /**
     * 暴露可选的程序化 Provider Bean；只有真实注入时才创建运行时。
     *
     * @param coordinator 运行时协调器
     * @return Provider
     */
    @Bean(destroyMethod = "")
    @Lazy
    @ConditionalOnMissingBean
    public PeachRpcServer peachRpcServer(PeachRpcRuntimeCoordinator coordinator) {
        return coordinator.server();
    }

    /**
     * 创建 Provider Bean 定义校验器。
     *
     * @return Provider Bean 定义校验器
     */
    @Bean
    @ConditionalOnMissingBean
    public static PeachRpcServiceBeanDefinitionValidator
            peachRpcServiceBeanDefinitionValidator() {
        return new PeachRpcServiceBeanDefinitionValidator();
    }

    /**
     * 创建 Consumer 引用注入处理器。
     *
     * @param coordinatorProvider 运行时协调器延迟提供器
     * @return Consumer 引用注入处理器
     */
    @Bean
    @ConditionalOnMissingBean
    public static PeachRpcReferenceBeanPostProcessor peachRpcReferenceBeanPostProcessor(
            ObjectProvider<PeachRpcRuntimeCoordinator> coordinatorProvider) {
        return new PeachRpcReferenceBeanPostProcessor(coordinatorProvider);
    }

    /**
     * 创建 Provider 服务导出处理器。
     *
     * @param coordinatorProvider 运行时协调器延迟提供器
     * @return Provider 服务导出处理器
     */
    @Bean
    @ConditionalOnMissingBean
    public static PeachRpcServiceBeanPostProcessor peachRpcServiceBeanPostProcessor(
            ObjectProvider<PeachRpcRuntimeCoordinator> coordinatorProvider) {
        return new PeachRpcServiceBeanPostProcessor(coordinatorProvider);
    }

    /**
     * 创建 Provider 生命周期适配器。
     *
     * @param coordinator 运行时协调器
     * @return Provider 生命周期适配器
     */
    @Bean
    @ConditionalOnMissingBean
    public PeachRpcServerLifecycle peachRpcServerLifecycle(
            PeachRpcRuntimeCoordinator coordinator) {
        return new PeachRpcServerLifecycle(coordinator);
    }

}
