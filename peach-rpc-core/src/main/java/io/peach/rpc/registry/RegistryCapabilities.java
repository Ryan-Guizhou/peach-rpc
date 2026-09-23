package io.peach.rpc.registry;

import java.util.Arrays;
import java.util.Set;

/**
 * 注册中心能力集合。
 *
 * @param values 能力集合
 */
public record RegistryCapabilities(Set<RegistryCapability> values) {

    /** 固化不可变能力集合。 */
    public RegistryCapabilities {
        values = values == null ? Set.of() : Set.copyOf(values);
    }

    /**
     * 创建能力集合。
     *
     * @param capabilities 能力列表
     * @return 能力集合
     */
    public static RegistryCapabilities of(RegistryCapability... capabilities) {
        return new RegistryCapabilities(Set.copyOf(Arrays.asList(capabilities)));
    }

    /**
     * 判断是否支持指定能力。
     *
     * @param capability 目标能力
     * @return 支持时返回 true
     */
    public boolean supports(RegistryCapability capability) {
        return values.contains(capability);
    }
}
