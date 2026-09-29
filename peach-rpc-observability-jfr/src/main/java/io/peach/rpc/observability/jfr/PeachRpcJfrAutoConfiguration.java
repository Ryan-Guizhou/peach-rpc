package io.peach.rpc.observability.jfr;

import io.peach.rpc.observability.RpcObserver;
import java.time.Duration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/** Peach RPC JFR 自动配置。 */
@AutoConfiguration
@ConditionalOnProperty(
        prefix = "peach.rpc.observability.jfr",
        name = "enabled",
        havingValue = "true")
public class PeachRpcJfrAutoConfiguration {

    /** 创建自动配置。 */
    public PeachRpcJfrAutoConfiguration() {
    }

    /**
     * 创建 JFR Observer。
     *
     * @return JFR Observer
     */
    @Bean
    @ConditionalOnMissingBean(
            name = "peachRpcJfrObserver")
    public RpcObserver peachRpcJfrObserver() {
        return new JfrRpcObserver(
                Duration.ofMillis(100));
    }
}
