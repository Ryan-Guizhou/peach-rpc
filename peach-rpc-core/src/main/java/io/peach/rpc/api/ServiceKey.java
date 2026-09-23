package io.peach.rpc.api;

import java.util.Objects;

/**
 * 服务唯一键，由接口名、版本和分组共同组成。
 *
 * @param serviceName 服务接口名称
 * @param version 服务版本
 * @param group 服务分组
 */
public record ServiceKey(String serviceName, String version, String group) {

    /** 校验并规范化服务键。 */
    public ServiceKey {
        Objects.requireNonNull(serviceName, "serviceName");
        version = normalize(version, "1.0.0");
        group = normalize(group, "default");
    }

    /**
     * 返回稳定的服务契约名称。
     *
     * @return 服务接口、版本和分组组成的名称
     */
    public String canonicalName() {
        return serviceName + ':' + version + ':' + group;
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
