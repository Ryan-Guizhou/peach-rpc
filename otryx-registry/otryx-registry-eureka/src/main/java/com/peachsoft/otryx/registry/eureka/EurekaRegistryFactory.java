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
@Extension("eureka")
public final class EurekaRegistryFactory implements RegistryFactory {

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
        long lease = number(options, "eurekaLeaseSeconds", 30L, 9L, 3600L);
        long heartbeat = number(options, "eurekaHeartbeatSeconds", 10L, 1L, lease - 1);
        long poll = number(options, "eurekaPollIntervalMillis", 1000L, 200L, 60000L);
        long timeout = number(options, "eurekaRequestTimeoutMillis", 3000L, 100L, 60000L);
        String username = options.providerOption("eurekaUsername", "");
        String password = options.providerOption("eurekaPassword", "");
        if (username.isBlank() != password.isBlank()) {
            throw new IllegalArgumentException(
                    "Eureka username and password must be configured together");
        }
        String credential = username.isBlank() ? "" : "Basic "
                + Base64.getEncoder().encodeToString(
                        (username + ":" + password).getBytes(StandardCharsets.UTF_8));
        HttpRegistryClient client = new HttpRegistryClient(
                options.endpoints().isEmpty()
                        ? List.of("http://127.0.0.1:8761/eureka") : options.endpoints(),
                "Authorization", credential, Duration.ofMillis(timeout));
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
