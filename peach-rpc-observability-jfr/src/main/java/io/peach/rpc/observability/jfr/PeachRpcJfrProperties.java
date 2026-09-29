package io.peach.rpc.observability.jfr;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Peach RPC JFR Adapter 配置。 */
@ConfigurationProperties("peach.rpc.observability.jfr")
public class PeachRpcJfrProperties {

    private boolean enabled;
    private Duration slowThreshold =
            Duration.ofMillis(100);

    /** 创建配置。 */
    public PeachRpcJfrProperties() {
    }

    /**
     * 返回是否启用 JFR Adapter。
     *
     * @return 是否启用 JFR Adapter
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用 JFR Adapter。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回慢调用 JFR Event 记录阈值。
     *
     * @return 慢调用阈值
     */
    public Duration getSlowThreshold() {
        return slowThreshold;
    }

    /**
     * 设置慢调用 JFR Event 记录阈值。
     *
     * @param slowThreshold 慢调用阈值
     */
    public void setSlowThreshold(
            Duration slowThreshold) {
        this.slowThreshold = slowThreshold;
    }
}
