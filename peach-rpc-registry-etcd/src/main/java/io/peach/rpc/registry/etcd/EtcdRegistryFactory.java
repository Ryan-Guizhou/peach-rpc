package io.peach.rpc.registry.etcd;

import io.peach.rpc.registry.Registry;
import io.peach.rpc.registry.RegistryFactory;
import io.peach.rpc.registry.RegistryOptions;
import io.peach.rpc.spi.Extension;
import java.util.List;

/** Etcd 注册中心工厂。 */
@Extension("etcd")
public final class EtcdRegistryFactory implements RegistryFactory {

    private static final long DEFAULT_LEASE_TTL_SECONDS = 30;
    private static final List<String> DEFAULT_ENDPOINTS =
            List.of("http://127.0.0.1:2379");

    /** 创建 Etcd 注册中心工厂。 */
    public EtcdRegistryFactory() {
    }

    @Override
    public Registry create(RegistryOptions options) {
        List<String> configured = options.endpoints().isEmpty()
                ? DEFAULT_ENDPOINTS
                : options.endpoints();
        long leaseTtlSeconds = Long.parseLong(options.providerOption(
                "leaseTtlSeconds",
                Long.toString(DEFAULT_LEASE_TTL_SECONDS)));
        if (leaseTtlSeconds <= 0) {
            throw new IllegalArgumentException(
                    "leaseTtlSeconds must be positive");
        }
        return new EtcdRegistry(
                configured.toArray(String[]::new),
                leaseTtlSeconds,
                options.namespace());
    }
}
