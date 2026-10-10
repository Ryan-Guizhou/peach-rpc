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
@Extension(ConsulProtocol.ADAPTER_TYPE)
public final class ConsulRegistryFactory implements RegistryFactory {

    private static final long DEFAULT_TTL_SECONDS = 30L;
    private static final long MIN_TTL_SECONDS = 9L;
    private static final long MAX_TTL_SECONDS = 3600L;
    private static final long DEFAULT_HEARTBEAT_SECONDS = 10L;
    private static final long MIN_HEARTBEAT_SECONDS = 1L;
    private static final long DEFAULT_POLL_INTERVAL_MILLIS = 1000L;
    private static final long MIN_POLL_INTERVAL_MILLIS = 200L;
    private static final long MAX_POLL_INTERVAL_MILLIS = 60000L;
    private static final long DEFAULT_REQUEST_TIMEOUT_MILLIS = 3000L;
    private static final long MIN_REQUEST_TIMEOUT_MILLIS = 100L;
    private static final long MAX_REQUEST_TIMEOUT_MILLIS = 60000L;

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
        long ttl = number(options, ConsulProtocol.OPTION_TTL_SECONDS,
                DEFAULT_TTL_SECONDS, MIN_TTL_SECONDS, MAX_TTL_SECONDS);
        long heartbeat = number(options, ConsulProtocol.OPTION_HEARTBEAT_SECONDS,
                DEFAULT_HEARTBEAT_SECONDS, MIN_HEARTBEAT_SECONDS, ttl - MIN_HEARTBEAT_SECONDS);
        long poll = number(options, ConsulProtocol.OPTION_POLL_INTERVAL_MILLIS,
                DEFAULT_POLL_INTERVAL_MILLIS, MIN_POLL_INTERVAL_MILLIS, MAX_POLL_INTERVAL_MILLIS);
        long timeout = number(options, ConsulProtocol.OPTION_REQUEST_TIMEOUT_MILLIS,
                DEFAULT_REQUEST_TIMEOUT_MILLIS, MIN_REQUEST_TIMEOUT_MILLIS, MAX_REQUEST_TIMEOUT_MILLIS);
        List<String> agentEndpoints = options.endpoints().isEmpty()
                ? List.of(ConsulProtocol.DEFAULT_AGENT_ENDPOINT) : options.endpoints();
        if (agentEndpoints.size() != 1) {
            throw new IllegalArgumentException(
                    "Consul Agent registration requires exactly one local Agent endpoint");
        }
        HttpRegistryClient client = new HttpRegistryClient(
                agentEndpoints,
                ConsulProtocol.TOKEN_HEADER,
                options.providerOption(ConsulProtocol.OPTION_TOKEN, ""),
                Duration.ofMillis(timeout));
        ConsulRegistryBackend backend = new ConsulRegistryBackend(
                client, options.namespace(),
                options.providerOption(ConsulProtocol.OPTION_DATACENTER, ""),
                options.providerOption(ConsulProtocol.OPTION_ENTERPRISE_NAMESPACE, ""), ttl);
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
