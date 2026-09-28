package io.peach.rpc.spring.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.peach.rpc.spring.annotation.PeachRpcReference;
import io.peach.rpc.spring.annotation.PeachRpcService;
import io.peach.rpc.spring.runtime.PeachRpcRuntimeCoordinator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Lazy;

/** Peach RPC 注解驱动自动配置测试。 */
class PeachRpcAutoConfigurationTest {

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
        contextRunner
                .withPropertyValues(
                        "peach.rpc.server.host=127.0.0.1",
                        "peach.rpc.server.port=0")
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
        contextRunner
                .withPropertyValues(
                        "peach.rpc.server.host=127.0.0.1",
                        "peach.rpc.server.port=0")
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
    void disabledConsumerShouldFailWhenReferenceExists() {
        contextRunner
                .withPropertyValues(
                        "peach.rpc.client.enabled=false")
                .withBean(ConsumerBean.class)
                .run(context ->
                        assertThat(context)
                                .hasFailed());
    }

    @Test
    void disabledProviderShouldFailWhenServiceExists() {
        contextRunner
                .withPropertyValues(
                        "peach.rpc.server.enabled=false")
                .withBean(ServiceBean.class)
                .run(context ->
                        assertThat(context)
                                .hasFailed());
    }

    @Test
    void lazyProviderShouldFailFast() {
        contextRunner.withBean(LazyServiceBean.class)
                .run(context ->
                        assertThat(context)
                                .hasFailed());
    }

    interface DemoService {
        String call();
    }

    @PeachRpcService(
            interfaceClass = DemoService.class,
            version = "1.0.0")
    static class ServiceBean implements DemoService {
        @Override
        public String call() {
            return "ok";
        }
    }

    @Lazy
    @PeachRpcService(
            interfaceClass = DemoService.class,
            version = "1.0.0")
    static class LazyServiceBean implements DemoService {
        @Override
        public String call() {
            return "ok";
        }
    }

    static class ConsumerBean {
        @PeachRpcReference(version = "1.0.0")
        private DemoService service;
    }
}
