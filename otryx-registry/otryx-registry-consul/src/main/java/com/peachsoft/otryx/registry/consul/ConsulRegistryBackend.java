package com.peachsoft.otryx.registry.consul;

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
import java.util.Map;
import java.util.Objects;

/**
 * Consul Agent 注册、TTL Check 续约和 Health API 实例映射。
 *
 * <p>仅对已通过健康检查的实例返回路由结果。
 *
 * @Author Ryan
 * @Version 1.0.0
 * @CreateTime 2026/10/10 18:00
 */
public final class ConsulRegistryBackend implements HttpRegistryBackend {

    private final HttpRegistryClient client;
    private final String namespace;
    private final String datacenter;
    private final String enterpriseNamespace;
    private final long ttlSeconds;

    /**
     * 配置 Consul Agent API。
     *
     * @param client HTTP 客户端
     * @param namespace OTRYX 逻辑命名空间（不等于 Consul Enterprise Namespace）
     * @param datacenter 读取服务的 Consul 数据中心
     * @param enterpriseNamespace 可选的 Consul Enterprise 命名空间
     * @param ttlSeconds TTL 秒数
     */
    public ConsulRegistryBackend(
            HttpRegistryClient client, String namespace,
            String datacenter, String enterpriseNamespace, long ttlSeconds) {
        this.client = Objects.requireNonNull(client, "client");
        this.namespace = namespace == null ? "" : namespace;
        this.datacenter = datacenter == null ? "" : datacenter;
        this.enterpriseNamespace = enterpriseNamespace == null ? "" : enterpriseNamespace;
        this.ttlSeconds = ttlSeconds;
    }

    /** @return Consul 适配器名称 */
    @Override
    public String type() {
        return "consul";
    }

    /**
     * 注册本地 Agent 服务与 TTL Check，然后立即标记 passing。
     *
     * @param instance RPC 实例
     * @throws Exception 请求失败
     */
    @Override
    public void register(ServiceInstance instance) throws Exception {
        HttpRegistryIdentity.requireRoutable(instance);
        String name = HttpRegistryIdentity.serviceName(namespace, instance.serviceKey());
        String id = HttpRegistryIdentity.scopedInstanceId(namespace, instance);
        Map<String, Object> body = Map.of(
                "ID", id,
                "Name", name,
                "Address", instance.endpoint().host(),
                "Port", instance.endpoint().port(),
                "Meta", HttpRegistryIdentity.metadata(instance),
                "Check", Map.of(
                        "TTL", ttlSeconds + "s",
                        "DeregisterCriticalServiceAfter", "1m",
                        "Status", "critical"));
        requireSuccess(client.request(
                "PUT", "/v1/agent/service/register" + namespaceQuery(), body));
        pass(id);
    }

    /**
     * 刷新 TTL；Agent 重启丢失 Check 后重新注册。
     *
     * @param instance RPC 实例
     * @throws Exception 请求失败
     */
    @Override
    public void renew(ServiceInstance instance) throws Exception {
        String id = HttpRegistryIdentity.scopedInstanceId(namespace, instance);
        HttpRegistryClient.Response response = client.request(
                "PUT", "/v1/agent/check/pass/" + encode("service:" + id)
                        + namespaceQuery(), null);
        if (response.status() == 404) {
            register(instance);
        } else {
            requireSuccess(response);
        }
    }

    private void pass(String id) throws Exception {
        requireSuccess(client.request(
                "PUT", "/v1/agent/check/pass/" + encode("service:" + id)
                        + namespaceQuery(), null));
    }

    /**
     * 在本地 Agent 注销实例，404 视为幂等删除。
     *
     * @param instance RPC 实例
     * @throws Exception 请求失败
     */
    @Override
    public void unregister(ServiceInstance instance) throws Exception {
        String id = HttpRegistryIdentity.scopedInstanceId(namespace, instance);
        HttpRegistryClient.Response response = client.request(
                "PUT", "/v1/agent/service/deregister/" + encode(id)
                        + namespaceQuery(), null);
        if (response.status() != 404) {
            requireSuccess(response);
        }
    }

    /**
     * 查询 Passing 的服务实例；保留完整服务键、实例标识与权重。
     *
     * @param key 完整 ServiceKey
     * @return 健康 Provider 列表
     * @throws Exception 查询失败
     */
    @Override
    public List<ServiceInstance> lookup(ServiceKey key) throws Exception {
        String name = HttpRegistryIdentity.serviceName(namespace, key);
        String url = "/v1/health/service/" + encode(name) + "?passing=true"
                + (datacenter.isBlank() ? "" : "&dc=" + encode(datacenter))
                + (enterpriseNamespace.isBlank()
                        ? "" : "&ns=" + encode(enterpriseNamespace));
        HttpRegistryClient.Response response = client.request("GET", url, null);
        if (response.status() == 404) {
            return List.of();
        }
        requireSuccess(response);
        if (!response.json().isArray()) {
            throw new IllegalStateException("Unexpected Consul health response shape");
        }
        List<ServiceInstance> result = new ArrayList<>();
        for (JsonNode item : response.json()) {
            JsonNode service = item.path("Service");
            String address = service.path("Address").asText("");
            if (address.isBlank()) {
                address = item.path("Node").path("Address").asText("");
            }
            JsonNode checks = item.path("Checks");
            if (checks.isArray()) {
                boolean healthy = true;
                for (JsonNode check : checks) {
                    if (!"passing".equals(check.path("Status").asText(""))) {
                        healthy = false;
                        break;
                    }
                }
                if (!healthy) {
                    continue;
                }
            }
            ServiceInstance mapped = HttpRegistryIdentity.fromRemote(
                    key, address, service.path("Port").asInt(-1),
                    metadata(service.path("Meta")));
            if (mapped != null) {
                result.add(mapped);
            }
        }
        return result;
    }

    private static Map<String, String> metadata(JsonNode input) {
        Map<String, String> result = new HashMap<>();
        if (input.isObject()) {
            input.fields().forEachRemaining(entry -> {
                if (entry.getValue().isTextual()) {
                    result.put(entry.getKey(), entry.getValue().asText());
                }
            });
        }
        return result;
    }

    private static void requireSuccess(HttpRegistryClient.Response response) {
        if (!response.successful()) {
            throw new IllegalStateException(
                    "Consul control-plane operation failed: status=" + response.status());
        }
    }

    private String namespaceQuery() {
        return enterpriseNamespace.isBlank()
                ? "" : "?ns=" + encode(enterpriseNamespace);
    }

    private static String encode(String input) {
        return URLEncoder.encode(input, StandardCharsets.UTF_8);
    }

    /** 关闭 HTTP 客户端。 */
    @Override
    public void close() {
        client.close();
    }
}
