package com.peachsoft.otryx.registry.eureka;

import com.peachsoft.otryx.registry.Registry;
import com.peachsoft.otryx.registry.RegistryFactory;
import com.peachsoft.otryx.registry.RegistryOptions;
import com.peachsoft.otryx.registry.http.HttpRegistry;
import com.peachsoft.otryx.registry.http.HttpRegistryClient;
import com.peachsoft.otryx.spi.Extension;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

/**
 * Eureka REST API 注册中心工厂。
 *
 * @Author Ryan
 * @Version 1.0.0
 * @CreateTime 2026/10/10 18:00
 */
@Extension(EurekaProtocol.ADAPTER_TYPE)
public final class EurekaRegistryFactory implements RegistryFactory {

    private static final long DEFAULT_LEASE_SECONDS = 30L;
    private static final long MIN_LEASE_SECONDS = 9L;
    private static final long MAX_LEASE_SECONDS = 3600L;
    private static final long DEFAULT_HEARTBEAT_SECONDS = 10L;
    private static final long MIN_HEARTBEAT_SECONDS = 1L;
    private static final long DEFAULT_POLL_INTERVAL_MILLIS = 1000L;
    private static final long MIN_POLL_INTERVAL_MILLIS = 200L;
    private static final long MAX_POLL_INTERVAL_MILLIS = 60000L;
    private static final long DEFAULT_REQUEST_TIMEOUT_MILLIS = 3000L;
    private static final long MIN_REQUEST_TIMEOUT_MILLIS = 100L;
    private static final long MAX_REQUEST_TIMEOUT_MILLIS = 60000L;

    /** 创建 Eureka 工厂。 */
    public EurekaRegistryFactory() {
    }

    /**
     * 配置 Eureka 服务注册、lease-renewal 和定期发现校对。
     *
     * @param options RegistryOptions
     * @return Eureka Registry
     */
    @Override
    public Registry create(RegistryOptions options) {
        long lease = number(options, EurekaProtocol.OPTION_LEASE_SECONDS,
                DEFAULT_LEASE_SECONDS, MIN_LEASE_SECONDS, MAX_LEASE_SECONDS);
        long heartbeat = number(options, EurekaProtocol.OPTION_HEARTBEAT_SECONDS,
                DEFAULT_HEARTBEAT_SECONDS, MIN_HEARTBEAT_SECONDS, lease - MIN_HEARTBEAT_SECONDS);
        long poll = number(options, EurekaProtocol.OPTION_POLL_INTERVAL_MILLIS,
                DEFAULT_POLL_INTERVAL_MILLIS, MIN_POLL_INTERVAL_MILLIS, MAX_POLL_INTERVAL_MILLIS);
        long timeout = number(options, EurekaProtocol.OPTION_REQUEST_TIMEOUT_MILLIS,
                DEFAULT_REQUEST_TIMEOUT_MILLIS, MIN_REQUEST_TIMEOUT_MILLIS, MAX_REQUEST_TIMEOUT_MILLIS);
        String username = options.providerOption(EurekaProtocol.OPTION_USERNAME, "");
        String password = options.providerOption(EurekaProtocol.OPTION_PASSWORD, "");
        if (username.isBlank() != password.isBlank()) {
            throw new IllegalArgumentException(
                    "Eureka username and password must be configured together");
        }
        String credential = username.isBlank() ? "" : EurekaProtocol.BASIC_AUTH_PREFIX
                + Base64.getEncoder().encodeToString(
                        (username + ":" + password).getBytes(StandardCharsets.UTF_8));
        HttpRegistryClient client = new HttpRegistryClient(
                options.endpoints().isEmpty()
                        ? List.of(EurekaProtocol.DEFAULT_ENDPOINT) : options.endpoints(),
                EurekaProtocol.AUTHORIZATION_HEADER, credential, Duration.ofMillis(timeout));
        return new HttpRegistry(
                new EurekaRegistryBackend(client, options.namespace(),
                        (int) heartbeat, (int) lease),
                Duration.ofMillis(poll), Duration.ofSeconds(heartbeat),
                options.observer());
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
