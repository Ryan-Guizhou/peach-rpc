package com.peachsoft.otryx.registry.consul;

import com.peachsoft.otryx.registry.Registry;
import com.peachsoft.otryx.registry.RegistryFactory;
import com.peachsoft.otryx.registry.RegistryOptions;
import com.peachsoft.otryx.registry.http.HttpRegistry;
import com.peachsoft.otryx.registry.http.HttpRegistryClient;
import com.peachsoft.otryx.spi.Extension;
import java.time.Duration;
import java.util.List;

/**
 * Consul Agent 与健康服务发现 HTTP Adapter 工厂。
 *
 * @Author Ryan
 * @Version 1.0.0
 * @CreateTime 2026/10/10 18:00
 */
@Extension("consul")
public final class ConsulRegistryFactory implements RegistryFactory {

    /** 创建 Consul 工厂。 */
    public ConsulRegistryFactory() {
    }

    /**
     * 创建控制面 Registry，HTTP 执行资源由实例持有。
     *
     * @param options RegistryOptions，厂商配置由 providerOptions 提供
     * @return Consul Registry
     */
    @Override
    public Registry create(RegistryOptions options) {
        long ttl = number(options, "consulTtlSeconds", 30L, 9L, 3600L);
        long heartbeat = number(options, "consulHeartbeatSeconds", 10L, 1L, ttl - 1);
        long poll = number(options, "consulPollIntervalMillis", 1000L, 200L, 60000L);
        long timeout = number(options, "consulRequestTimeoutMillis", 3000L, 100L, 60000L);
        List<String> agentEndpoints = options.endpoints().isEmpty()
                ? List.of("http://127.0.0.1:8500") : options.endpoints();
        if (agentEndpoints.size() != 1) {
            throw new IllegalArgumentException(
                    "Consul Agent registration requires exactly one local Agent endpoint");
        }
        HttpRegistryClient client = new HttpRegistryClient(
                agentEndpoints,
                "X-Consul-Token",
                options.providerOption("consulToken", ""),
                Duration.ofMillis(timeout));
        ConsulRegistryBackend backend = new ConsulRegistryBackend(
                client, options.namespace(),
                options.providerOption("consulDatacenter", ""),
                options.providerOption("consulEnterpriseNamespace", ""), ttl);
        return new HttpRegistry(
                backend, Duration.ofMillis(poll),
                Duration.ofSeconds(heartbeat), options.observer());
    }

    private static long number(
            RegistryOptions options, String key, long fallback, long min, long max) {
        long parsed;
        try {
            parsed = Long.parseLong(options.providerOption(key, Long.toString(fallback)));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Invalid registry numeric option: " + key);
        }
        if (parsed < min || parsed > max) {
            throw new IllegalArgumentException("Registry option out of range: " + key);
        }
        return parsed;
    }
}
