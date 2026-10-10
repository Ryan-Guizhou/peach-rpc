package io.peach.rpc.observability.opentelemetry;

import io.opentelemetry.api.OpenTelemetry;
import io.peach.rpc.observability.RpcTracingBridge;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Peach RPC OpenTelemetry 自动配置。 */
@AutoConfiguration
@ConditionalOnClass(OpenTelemetry.class)
public class PeachRpcOpenTelemetryAutoConfiguration {

    /** 创建自动配置。 */
    public PeachRpcOpenTelemetryAutoConfiguration() {
    }

    /**
     * 创建 OpenTelemetry Trace Bridge。
     *
     * @param openTelemetry OpenTelemetry
     * @return Trace Bridge
     */
    @Bean
    @ConditionalOnBean(OpenTelemetry.class)
    @ConditionalOnMissingBean(RpcTracingBridge.class)
    public RpcTracingBridge peachRpcTracingBridge(
            OpenTelemetry openTelemetry) {
        return new OpenTelemetryRpcTracingBridge(
                openTelemetry);
    }
}
