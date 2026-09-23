package io.peach.rpc.registry;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Registry Adapter 的公共启动配置。
 *
 * @param endpoints 注册中心端点
 * @param namespace 逻辑命名空间
 * @param providerOptions Adapter 私有配置
 */
public record RegistryOptions(
        List<String> endpoints,
        String namespace,
        Map<String, String> providerOptions) {

    /** 校验并固化 Registry 配置。 */
    public RegistryOptions {
        endpoints = endpoints == null
                ? List.of()
                : endpoints.stream()
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .toList();
        namespace = namespace == null || namespace.isBlank()
                ? "default"
                : namespace.trim();
        providerOptions = providerOptions == null
                ? Map.of()
                : Map.copyOf(providerOptions);
    }

    /**
     * 从逗号分隔的端点配置创建 RegistryOptions。
     *
     * @param endpoints 逗号分隔端点
     * @param namespace 逻辑命名空间
     * @param providerOptions Adapter 私有配置
     * @return Registry 配置
     */
    public static RegistryOptions fromCsv(
            String endpoints,
            String namespace,
            Map<String, String> providerOptions) {
        List<String> values = endpoints == null || endpoints.isBlank()
                ? List.of()
                : Arrays.stream(endpoints.split(","))
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .toList();
        return new RegistryOptions(values, namespace, providerOptions);
    }

    /**
     * 读取 Adapter 私有配置。
     *
     * @param key 配置键
     * @param fallback 默认值
     * @return 配置值
     */
    public String providerOption(String key, String fallback) {
        return providerOptions.getOrDefault(key, fallback);
    }
}
