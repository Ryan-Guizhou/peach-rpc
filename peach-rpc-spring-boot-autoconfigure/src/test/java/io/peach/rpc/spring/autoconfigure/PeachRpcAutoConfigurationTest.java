package io.peach.rpc.spring.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.peach.rpc.core.PeachRpcClient;
import io.peach.rpc.core.PeachRpcServer;
import io.peach.rpc.observability.RpcSecurityMode;
import io.peach.rpc.transport.RpcTransportOptions;
import io.peach.rpc.spring.annotation.PeachRpcReference;
import io.peach.rpc.spring.annotation.PeachRpcService;
import io.peach.rpc.spring.runtime.PeachRpcRuntimeCoordinator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;

/** Peach RPC 注解驱动自动配置测试。 */
public class PeachRpcAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    PeachRpcAutoConfiguration.class));

    @Test
    void noRpcAnnotationsShouldNotCreateRuntime() {
        contextRunner.run(context -> {
            PeachRpcRuntimeCoordinator coordinator =
                    context.getBean(
                            PeachRpcRuntimeCoordinator.class);

            assertThat(coordinator.clientIfCreated()).isEmpty();
            assertThat(coordinator.serverIfCreated()).isEmpty();
        });
    }

    @Test
    void explicitEnableShouldNotCreateRuntimeWithoutUsage() {
        contextRunner
                .withPropertyValues(
                        "peach.rpc.client.enabled=true",
                        "peach.rpc.server.enabled=true")
                .run(context -> {
                    PeachRpcRuntimeCoordinator coordinator =
                            context.getBean(
                                    PeachRpcRuntimeCoordinator.class);

                    assertThat(coordinator.clientIfCreated()).isEmpty();
                    assertThat(coordinator.serverIfCreated()).isEmpty();
                });
    }

    @Test
    void referenceShouldCreateOnlyConsumerRuntime() {
        contextRunner.withBean(ConsumerBean.class)
                .run(context -> {
                    PeachRpcRuntimeCoordinator coordinator =
                            context.getBean(
                                    PeachRpcRuntimeCoordinator.class);

                    assertThat(coordinator.clientIfCreated()).isPresent();
                    assertThat(coordinator.serverIfCreated()).isEmpty();
                });
    }

    @Test
    void serviceShouldCreateOnlyProviderRuntime() {
        providerRunner()
                .withBean(ServiceBean.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    PeachRpcRuntimeCoordinator coordinator =
                            context.getBean(
                                    PeachRpcRuntimeCoordinator.class);

                    assertThat(coordinator.clientIfCreated()).isEmpty();
                    assertThat(coordinator.serverIfCreated()).isPresent();
                });
    }

    @Test
    void referenceAndServiceShouldCreateBothRuntimes() {
        providerRunner()
                .withBean(ServiceBean.class)
                .withBean(ConsumerBean.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    PeachRpcRuntimeCoordinator coordinator =
                            context.getBean(
                                    PeachRpcRuntimeCoordinator.class);

                    assertThat(coordinator.clientIfCreated()).isPresent();
                    assertThat(coordinator.serverIfCreated()).isPresent();
                });
    }

    @Test
    void multipleServicesShouldRegisterBeforeServerStarts() {
        providerRunner()
                .withBean(ServiceBean.class)
                .withBean(SecondServiceBean.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(
                            PeachRpcServer.class);
                });
    }

    @Test
    void disabledConsumerShouldFailWhenReferenceExists() {
        contextRunner
                .withPropertyValues(
                        "peach.rpc.client.enabled=false")
                .withBean(ConsumerBean.class)
                .run(context ->
                        assertThat(context).hasFailed());
    }

    @Test
    void disabledProviderShouldFailWhenServiceExists() {
        contextRunner
                .withPropertyValues(
                        "peach.rpc.server.enabled=false")
                .withBean(ServiceBean.class)
                .run(context ->
                        assertThat(context).hasFailed());
    }

    @Test
    void nonInterfaceReferenceShouldFailFast() {
        contextRunner
                .withBean(InvalidReferenceBean.class)
                .run(context ->
                        assertThat(context).hasFailed());
    }

    @Test
    void mismatchedServiceInterfaceShouldFailFast() {
        contextRunner
                .withBean(MismatchedServiceBean.class)
                .run(context ->
                        assertThat(context).hasFailed());
    }

    @Test
    void lazyProviderShouldFailFast() {
        contextRunner.withBean(LazyServiceBean.class)
                .run(context ->
                        assertThat(context).hasFailed());
    }

    @Test
    void prototypeProviderShouldFailFast() {
        contextRunner
                .withUserConfiguration(
                        PrototypeProviderConfiguration.class)
                .run(context ->
                        assertThat(context).hasFailed());
    }

    @Test
    void disabledFrameworkShouldCreateNoRpcBeans() {
        contextRunner
                .withPropertyValues(
                        "peach.rpc.enabled=false")
                .withBean(ConsumerBean.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(
                            PeachRpcRuntimeCoordinator.class);
                    assertThat(context).doesNotHaveBean(
                            PeachRpcClient.class);
                    assertThat(context).doesNotHaveBean(
                            PeachRpcServer.class);
                });
    }

    @Test
    void tlsPropertiesShouldBindToTransportSecurityOptions() {
        contextRunner
                .withPropertyValues(
                        "peach.rpc.transport.security.mode=MTLS",
                        "peach.rpc.transport.security.certificate-path=/tmp/server.crt",
                        "peach.rpc.transport.security.private-key-path=/tmp/server.key",
                        "peach.rpc.transport.security.trust-certificate-path=/tmp/ca.crt",
                        "peach.rpc.transport.security.hostname-verification=false",
                        "peach.rpc.transport.security.handshake-timeout=5s",
                        "peach.rpc.transport.security.reload-interval=45s",
                        "peach.rpc.transport.security.expiry-warning-threshold=10d")
                .run(context -> {
                    RpcTransportOptions options =
                            context.getBean(
                                    RpcTransportOptions.class);

                    assertThat(options.security().mode())
                            .isEqualTo(
                                    RpcSecurityMode.MTLS);
                    assertThat(
                            options.security()
                                    .certificatePath())
                            .isEqualTo("/tmp/server.crt");
                    assertThat(
                            options.security()
                                    .privateKeyPath())
                            .isEqualTo("/tmp/server.key");
                    assertThat(
                            options.security()
                                    .trustCertificatePath())
                            .isEqualTo("/tmp/ca.crt");
                    assertThat(
                            options.security()
                                    .hostnameVerification())
                            .isFalse();
                    assertThat(
                            options.security()
                                    .handshakeTimeout())
                            .isEqualTo(
                                    java.time.Duration
                                            .ofSeconds(5));
                    assertThat(
                            options.security()
                                    .reloadInterval())
                            .isEqualTo(
                                    java.time.Duration
                                            .ofSeconds(45));
                    assertThat(
                            options.security()
                                    .expiryWarningThreshold())
                            .isEqualTo(
                                    java.time.Duration
                                            .ofDays(10));
                });
    }

    @Test
    void rawClientInjectionShouldCreateConsumerRuntime() {
        contextRunner
                .withBean(RawClientBean.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    PeachRpcRuntimeCoordinator coordinator =
                            context.getBean(
                                    PeachRpcRuntimeCoordinator.class);

                    assertThat(coordinator.clientIfCreated()).isPresent();
                    assertThat(coordinator.serverIfCreated()).isEmpty();
                });
    }

    @Test
    void rawServerInjectionShouldNotAutoStartProvider() {
        contextRunner
                .withPropertyValues(
                        "peach.rpc.server.host=0.0.0.0")
                .withBean(RawServerBean.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    PeachRpcRuntimeCoordinator coordinator =
                            context.getBean(
                                    PeachRpcRuntimeCoordinator.class);

                    assertThat(coordinator.serverIfCreated()).isPresent();
                    assertThat(coordinator.autoStartServerIfCreated())
                            .isEmpty();
                });
    }

    private ApplicationContextRunner providerRunner() {
        return contextRunner.withPropertyValues(
                "peach.rpc.server.host=127.0.0.1",
                "peach.rpc.server.port=0");
    }

    public interface DemoService {
        String call();
    }

    public interface SecondDemoService {
        String second();
    }

    public interface OtherService {
        String other();
    }

    @PeachRpcService(
            interfaceClass = DemoService.class,
            version = "1.0.0")
    public static class ServiceBean implements DemoService {
        @Override
        public String call() {
            return "ok";
        }
    }

    @PeachRpcService(
            interfaceClass = SecondDemoService.class,
            version = "1.0.0")
    public static class SecondServiceBean
            implements SecondDemoService {
        @Override
        public String second() {
            return "second";
        }
    }

    @PeachRpcService(
            interfaceClass = DemoService.class,
            version = "1.0.0")
    public static class MismatchedServiceBean
            implements OtherService {
        @Override
        public String other() {
            return "other";
        }
    }

    @Lazy
    @PeachRpcService(
            interfaceClass = DemoService.class,
            version = "1.0.0")
    public static class LazyServiceBean implements DemoService {
        @Override
        public String call() {
            return "ok";
        }
    }

    @PeachRpcService(
            interfaceClass = DemoService.class,
            version = "1.0.0")
    public static class PrototypeServiceBean
            implements DemoService {
        @Override
        public String call() {
            return "prototype";
        }
    }

    public static class ConsumerBean {
        @PeachRpcReference(version = "1.0.0")
        private DemoService service;
    }

    public static class InvalidReferenceBean {
        @PeachRpcReference
        private String service;
    }

    public static class RawClientBean {
        public RawClientBean(PeachRpcClient client) {
        }
    }

    public static class RawServerBean {
        public RawServerBean(PeachRpcServer server) {
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class PrototypeProviderConfiguration {

        @Bean
        @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
        public PrototypeServiceBean prototypeServiceBean() {
            return new PrototypeServiceBean();
        }
    }
}
