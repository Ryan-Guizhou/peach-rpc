package io.peach.rpc.observability.micrometer;

import io.micrometer.core.instrument.MeterRegistry;
import io.peach.rpc.observability.RpcObserver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Peach RPC Micrometer 自动配置。 */
@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
public class PeachRpcMicrometerAutoConfiguration {

    /** 创建自动配置。 */
    public PeachRpcMicrometerAutoConfiguration() {
    }

    /**
     * 创建 Micrometer Observer。
     *
     * @param registry MeterRegistry
     * @return RPC Observer
     */
    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean(
            name = "peachRpcMicrometerObserver")
    public RpcObserver peachRpcMicrometerObserver(
            MeterRegistry registry) {
        return new MicrometerRpcObserver(registry);
    }
}
