package io.peach.rpc.spring.autoconfigure;

import io.peach.rpc.core.PeachRpcClient;
import io.peach.rpc.registry.Registry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Peach RPC 自动配置测试。
 */
class PeachRpcAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PeachRpcAutoConfiguration.class));

    /**
     * 验证默认配置只创建 Consumer，并使用内存注册中心。
     */
    @Test
    void shouldCreateDefaultConsumerRuntime() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(Registry.class);
            assertThat(context).hasSingleBean(PeachRpcClient.class);
            assertThat(context).doesNotHaveBean("peachRpcServer");
        });
    }
}
