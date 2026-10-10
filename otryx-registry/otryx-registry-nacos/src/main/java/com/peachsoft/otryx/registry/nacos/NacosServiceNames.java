package com.peachsoft.otryx.registry.nacos;

import com.peachsoft.otryx.api.ServiceKey;
import java.util.Objects;

/**
 * OTRYX RPC 服务键与 Nacos serviceName 的稳定映射。
 *
 * @Author Ryan
 * @Version 1.0.0-SNAPSHOT
 * @CreateTime 2026/9/28 11:35
 */
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
