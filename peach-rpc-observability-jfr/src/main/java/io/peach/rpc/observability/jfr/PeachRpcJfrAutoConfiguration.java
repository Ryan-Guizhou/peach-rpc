package io.peach.rpc.observability.jfr;

import io.peach.rpc.observability.RpcObserver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Peach RPC JFR 自动配置。 */
@AutoConfiguration
@ConditionalOnProperty(
        prefix = "peach.rpc.observability.jfr",
        name = "enabled",
        havingValue = "true")
@EnableConfigurationProperties(PeachRpcJfrProperties.class)
public class PeachRpcJfrAutoConfiguration {

    /** 创建自动配置。 */
    public PeachRpcJfrAutoConfiguration() {
    }

    /**
     * 创建 JFR Observer。
     *
     * @param properties JFR 配置
     * @return JFR Observer
     */
    @Bean
    @ConditionalOnMissingBean(
            name = "peachRpcJfrObserver")
    public RpcObserver peachRpcJfrObserver(
            PeachRpcJfrProperties properties) {
        return new JfrRpcObserver(
                properties.getSlowThreshold());
    }
}
