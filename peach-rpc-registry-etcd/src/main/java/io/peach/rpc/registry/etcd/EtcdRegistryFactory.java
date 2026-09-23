package io.peach.rpc.registry.etcd;

import io.peach.rpc.registry.Registry;
import io.peach.rpc.registry.RegistryFactory;
import io.peach.rpc.spi.Extension;
import java.util.Arrays;
import java.util.Map;

/** Etcd 注册中心工厂。 */
@Extension("etcd")
public final class EtcdRegistryFactory implements RegistryFactory {

    /**
     * 创建 Etcd 注册中心工厂。
     */
    public EtcdRegistryFactory() {
    }

    private static final long DEFAULT_LEASE_TTL_SECONDS = 30;

    @Override
    public Registry create(Map<String, String> options) {
        String configuredEndpoints = options.getOrDefault(
                "endpoints", "http://127.0.0.1:2379");
        String[] endpoints = Arrays.stream(configuredEndpoints.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toArray(String[]::new);
        if (endpoints.length == 0) {
            throw new IllegalArgumentException("Etcd endpoints must not be empty");
        }

        long leaseTtlSeconds = Long.parseLong(options.getOrDefault(
                "leaseTtlSeconds", Long.toString(DEFAULT_LEASE_TTL_SECONDS)));
        if (leaseTtlSeconds <= 0) {
            throw new IllegalArgumentException("leaseTtlSeconds must be positive");
        }
        return new EtcdRegistry(endpoints, leaseTtlSeconds);
    }
}
