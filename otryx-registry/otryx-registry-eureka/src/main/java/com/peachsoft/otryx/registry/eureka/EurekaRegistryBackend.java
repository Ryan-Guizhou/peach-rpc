package com.peachsoft.otryx.registry.eureka;

import com.fasterxml.jackson.databind.JsonNode;
import com.peachsoft.otryx.api.ServiceInstance;
import com.peachsoft.otryx.api.ServiceKey;
import com.peachsoft.otryx.registry.http.HttpRegistryBackend;
import com.peachsoft.otryx.registry.http.HttpRegistryClient;
import com.peachsoft.otryx.registry.http.HttpRegistryIdentity;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Eureka REST 注册、租约续期和 UP 实例发现。
 *
 * <p>Eureka 无原生服务变更推送；订阅通过共享有界控制面周期轮询实现。
 *
 * @Author Ryan
 * @Version 1.0.0
 * @CreateTime 2026/10/10 18:00
 */
public final class EurekaRegistryBackend implements HttpRegistryBackend {

    private final HttpRegistryClient client;
    private final String namespace;
    private final int renewalSeconds;
    private final int leaseSeconds;

    /**
     * 构造 Eureka HTTP 控制面。
     *
     * @param client HTTP Client
     * @param namespace 逻辑命名空间
     * @param renewalSeconds lease 更新周期
     * @param leaseSeconds lease 过期时长
     */
    public EurekaRegistryBackend(
            HttpRegistryClient client, String namespace,
            int renewalSeconds, int leaseSeconds) {
        this.client = Objects.requireNonNull(client, "client");
        this.namespace = namespace == null ? "" : namespace;
        this.renewalSeconds = renewalSeconds;
        this.leaseSeconds = leaseSeconds;
    }

    /** @return Eureka 适配器名称 */
    @Override
    public String type() {
        return "eureka";
    }

    /**
     * 注册业务实例，发布 UP 状态与标准 Eureka InstanceInfo。
     *
     * @param instance Provider 实例
     * @throws Exception HTTP 失败
     */
    @Override
    public void register(ServiceInstance instance) throws Exception {
        HttpRegistryIdentity.requireRoutable(instance);
        String app = appName(instance.serviceKey());
        Map<String, Object> payload = Map.of("instance", Map.ofEntries(
                Map.entry("instanceId", instanceId(instance)),
                Map.entry("app", app),
                Map.entry("hostName", instance.endpoint().host()),
                Map.entry("ipAddr", instance.endpoint().host()),
                Map.entry("vipAddress", app),
                Map.entry("secureVipAddress", app),
                Map.entry("status", "UP"),
                Map.entry("port", Map.of("$", instance.endpoint().port(), "@enabled", "true")),
                Map.entry("securePort", Map.of("$", 443, "@enabled", "false")),
                Map.entry("dataCenterInfo", Map.of(
                        "@class", "com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo",
                        "name", "MyOwn")),
                Map.entry("leaseInfo", Map.of(
                        "renewalIntervalInSecs", renewalSeconds,
                        "durationInSecs", leaseSeconds)),
                Map.entry("metadata", HttpRegistryIdentity.metadata(instance))));
        requireSuccess(client.request("POST", "/apps/" + encode(app), payload));
    }

    /**
     * 续约 Lease，Eureka 返回 404 时重新注册。
     *
     * @param instance Provider 实例
     * @throws Exception HTTP 失败
     */
    @Override
    public void renew(ServiceInstance instance) throws Exception {
        String path = "/apps/" + encode(appName(instance.serviceKey()))
                + "/" + encode(instanceId(instance));
        HttpRegistryClient.Response response = client.request("PUT", path, null);
        if (response.status() == 404) {
            register(instance);
        } else {
            requireSuccess(response);
        }
    }

    /**
     * 注销 Provider；不存在的实例视为已注销。
     *
     * @param instance Provider 实例
     * @throws Exception HTTP 失败
     */
    @Override
    public void unregister(ServiceInstance instance) throws Exception {
        String path = "/apps/" + encode(appName(instance.serviceKey()))
                + "/" + encode(instanceId(instance));
        HttpRegistryClient.Response response = client.request("DELETE", path, null);
        if (response.status() != 404) {
            requireSuccess(response);
        }
    }

    /**
     * 查询目标 Application，过滤非 UP、非法和非 OTRYX 元数据实例。
     *
     * @param key 完整服务键
     * @return 可路由 RPC Provider
     * @throws Exception HTTP 失败
     */
    @Override
    public List<ServiceInstance> lookup(ServiceKey key) throws Exception {
        String app = appName(key);
        HttpRegistryClient.Response response = client.request(
                "GET", "/apps/" + encode(app), null);
        if (response.status() == 404) {
            return List.of();
        }
        requireSuccess(response);
        JsonNode application = response.json().path("application");
        if (application.isMissingNode()) {
            throw new IllegalStateException("Unexpected Eureka application response shape");
        }
        JsonNode instances = application.path("instance");
        List<ServiceInstance> result = new ArrayList<>();
        if (instances.isArray()) {
            instances.forEach(node -> addInstance(key, node, result));
        } else if (instances.isObject()) {
            addInstance(key, instances, result);
        }
        return result;
    }

    private static void addInstance(
            ServiceKey key, JsonNode source, List<ServiceInstance> target) {
        if (!"UP".equals(source.path("status").asText(""))) {
            return;
        }
        String overridden = source.path("overriddenstatus").asText("");
        if ("OUT_OF_SERVICE".equals(overridden) || "DOWN".equals(overridden)) {
            return;
        }
        JsonNode portNode = source.path("port");
        int port = portNode.isObject()
                ? portNode.path("$").asInt(-1) : portNode.asInt(-1);
        String host = source.path("ipAddr").asText("");
        if (host.isBlank()) {
            host = source.path("hostName").asText("");
        }
        ServiceInstance mapped = HttpRegistryIdentity.fromRemote(
                key, host, port, metadata(source.path("metadata")));
        if (mapped != null) {
            target.add(mapped);
        }
    }

    private static Map<String, String> metadata(JsonNode input) {
        Map<String, String> result = new HashMap<>();
        if (input.isObject()) {
            input.fields().forEachRemaining(entry -> {
                if (entry.getValue().isValueNode()) {
                    result.put(entry.getKey(), entry.getValue().asText());
                }
            });
        }
        return result;
    }

    private static void requireSuccess(HttpRegistryClient.Response response) {
        if (!response.successful()) {
            throw new IllegalStateException(
                    "Eureka control-plane operation failed: status=" + response.status());
        }
    }

    private String appName(ServiceKey key) {
        return HttpRegistryIdentity.serviceName(namespace, key)
                .toUpperCase(Locale.ROOT);
    }

    private String instanceId(ServiceInstance instance) {
        return HttpRegistryIdentity.scopedInstanceId(namespace, instance);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** 关闭 Eureka HTTP 客户端。 */
    @Override
    public void close() {
        client.close();
    }
}
