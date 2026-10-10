package com.peachsoft.otryx.registry;

import com.peachsoft.otryx.observability.RpcObserver;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Registry Adapter 的公共启动配置。
 *
 * @param endpoints 注册中心端点
 * @param namespace 逻辑命名空间
 * @param providerOptions Adapter 私有配置
 * @param observer 控制面 Observer
 */
public record RegistryOptions(
        List<String> endpoints,
        String namespace,
        Map<String, String> providerOptions,
        RpcObserver observer) {

    /**
     * 保留原三参数构造方式，默认使用 NOOP Observer。
     *
     * @param endpoints 注册中心端点
     * @param namespace 逻辑命名空间
     * @param providerOptions Adapter 私有配置
     */
    public RegistryOptions(
            List<String> endpoints,
            String namespace,
            Map<String, String> providerOptions) {
        this(
                endpoints,
                namespace,
                providerOptions,
                RpcObserver.noop());
    }

    /** 校验并固化 Registry 配置。 */
    public RegistryOptions {
        endpoints = endpoints == null
                ? List.of()
                : endpoints.stream()
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .toList();
        namespace = namespace == null
                ? ""
                : namespace.trim();
        providerOptions = providerOptions == null
                ? Map.of()
                : Map.copyOf(providerOptions);
        observer = observer == null
                ? RpcObserver.noop()
                : observer;
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
        return fromCsv(
                endpoints,
                namespace,
                providerOptions,
                RpcObserver.noop());
    }

    /**
     * 从逗号分隔的端点配置创建 RegistryOptions。
     *
     * @param endpoints 逗号分隔端点
     * @param namespace 逻辑命名空间
     * @param providerOptions Adapter 私有配置
     * @param observer 控制面 Observer
     * @return Registry 配置
     */
    public static RegistryOptions fromCsv(
            String endpoints,
            String namespace,
            Map<String, String> providerOptions,
            RpcObserver observer) {
        List<String> values = endpoints == null || endpoints.isBlank()
                ? List.of()
                : Arrays.stream(endpoints.split(","))
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .toList();
        return new RegistryOptions(
                values,
                namespace,
                providerOptions,
                observer);
    }

    /**
     * 返回脱敏后的配置描述。
     *
     * <p>Registry Credential 不允许通过 record 默认 toString 泄露。
     *
     * @return 脱敏配置描述
     */
    @Override
    public String toString() {
        Map<String, String> safeOptions =
                new java.util.LinkedHashMap<>();
        providerOptions.forEach((key, value) ->
                safeOptions.put(
                        key,
                        sensitive(key)
                                ? "***"
                                : value));
        List<String> safeEndpoints = endpoints.stream()
                .map(RegistryOptions::redactEndpoint)
                .toList();
        return "RegistryOptions[endpoints="
                + safeEndpoints
                + ", namespace="
                + namespace
                + ", providerOptions="
                + safeOptions
                + ", observer="
                + observer.getClass().getName()
                + ']';
    }

    private static String redactEndpoint(
            String endpoint) {
        if (endpoint == null) {
            return null;
        }
        int userInfoEnd = endpoint.lastIndexOf('@');
        if (userInfoEnd < 0) {
            return endpoint;
        }
        return "***@" + endpoint.substring(userInfoEnd + 1);
    }

    private static boolean sensitive(String key) {
        String normalized =
                key == null
                        ? ""
                        : key.toLowerCase(
                                java.util.Locale.ROOT)
                                .replace("_", "")
                                .replace("-", "");
        return normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("token")
                || normalized.contains("credential")
                || normalized.contains("privatekey");
    }

    /**
     * 读取 Adapter 私有配置。
     *
     * @param key 配置键
     * @param fallback 默认值
     * @return 配置值
     */
    public String providerOption(
            String key,
            String fallback) {
        return providerOptions.getOrDefault(
                key,
                fallback);
    }
}
