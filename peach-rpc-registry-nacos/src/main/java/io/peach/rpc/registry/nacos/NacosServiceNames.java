package io.peach.rpc.registry.nacos;

import io.peach.rpc.api.ServiceKey;
import java.util.Objects;

/** Peach RPC 服务键与 Nacos serviceName 的稳定映射。 */
final class NacosServiceNames {

    private NacosServiceNames() {
    }

    static String serviceName(ServiceKey key) {
        Objects.requireNonNull(key, "key");
        String value = key.canonicalName();
        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    "Nacos service name must not be blank");
        }
        return value;
    }
}
