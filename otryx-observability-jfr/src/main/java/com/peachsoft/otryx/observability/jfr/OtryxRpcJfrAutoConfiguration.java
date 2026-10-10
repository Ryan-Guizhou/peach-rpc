package com.peachsoft.otryx.observability.jfr;

import com.peachsoft.otryx.observability.RpcObserver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** OTRYX RPC JFR 自动配置。 */
@AutoConfiguration
@ConditionalOnProperty(
        prefix = "otryx.rpc.observability.jfr",
        name = "enabled",
        havingValue = "true")
@EnableConfigurationProperties(OtryxRpcJfrProperties.class)
public class OtryxRpcJfrAutoConfiguration {

    /** 创建自动配置。 */
    public OtryxRpcJfrAutoConfiguration() {
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
            OtryxRpcJfrProperties properties) {
        return new JfrRpcObserver(
                properties.getSlowThreshold());
    }
}
