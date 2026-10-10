package com.peachsoft.otryx.registry.eureka;

import com.fasterxml.jackson.databind.JsonNode;
import com.peachsoft.otryx.api.ServiceInstance;
import com.peachsoft.otryx.api.ServiceKey;
import com.peachsoft.otryx.registry.http.HttpRegistryBackend;
import com.peachsoft.otryx.registry.http.HttpRegistryClient;
import com.peachsoft.otryx.registry.http.HttpRegistryIdentity;
import java.net.HttpURLConnection;
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

    /**
     * 返回相应配置或运行状态。
     *
     * @return Eureka 适配器名称
     */
    @Override
    public String type() {
        return EurekaProtocol.ADAPTER_TYPE;
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
        Map<String, Object> payload = Map.of(EurekaProtocol.FIELD_INSTANCE, Map.ofEntries(
                Map.entry(EurekaProtocol.FIELD_INSTANCE_ID, instanceId(instance)),
                Map.entry(EurekaProtocol.FIELD_APP, app),
                Map.entry(EurekaProtocol.FIELD_HOST_NAME, instance.endpoint().host()),
                Map.entry(EurekaProtocol.FIELD_IP_ADDRESS, instance.endpoint().host()),
                Map.entry(EurekaProtocol.FIELD_VIP_ADDRESS, app),
                Map.entry(EurekaProtocol.FIELD_SECURE_VIP_ADDRESS, app),
                Map.entry(EurekaProtocol.FIELD_STATUS, EurekaProtocol.STATUS_UP),
                Map.entry(EurekaProtocol.FIELD_PORT, Map.of(
                        EurekaProtocol.FIELD_VALUE, instance.endpoint().port(),
                        EurekaProtocol.FIELD_ENABLED, EurekaProtocol.PORT_ENABLED)),
                Map.entry(EurekaProtocol.FIELD_SECURE_PORT, Map.of(
                        EurekaProtocol.FIELD_VALUE, EurekaProtocol.DEFAULT_DISABLED_SECURE_PORT,
                        EurekaProtocol.FIELD_ENABLED, EurekaProtocol.SECURE_PORT_DISABLED)),
                Map.entry(EurekaProtocol.FIELD_DATA_CENTER_INFO, Map.of(
                        EurekaProtocol.FIELD_CLASS, EurekaProtocol.DEFAULT_DATA_CENTER_CLASS,
                        EurekaProtocol.FIELD_NAME, EurekaProtocol.DEFAULT_DATA_CENTER_NAME)),
                Map.entry(EurekaProtocol.FIELD_LEASE_INFO, Map.of(
                        EurekaProtocol.FIELD_RENEWAL_INTERVAL_SECONDS, renewalSeconds,
                        EurekaProtocol.FIELD_LEASE_DURATION_SECONDS, leaseSeconds)),
                Map.entry(EurekaProtocol.FIELD_METADATA, HttpRegistryIdentity.metadata(instance))));
        requireSuccess(client.request(
                HttpRegistryClient.METHOD_POST,
                EurekaProtocol.PATH_APPLICATIONS_PREFIX + encode(app), payload));
    }

    /**
     * 续约 Lease，Eureka 返回 404 时重新注册。
     *
     * @param instance Provider 实例
     * @throws Exception HTTP 失败
     */
    @Override
    public void renew(ServiceInstance instance) throws Exception {
        String path = EurekaProtocol.PATH_APPLICATIONS_PREFIX + encode(appName(instance.serviceKey()))
                + "/" + encode(instanceId(instance));
        HttpRegistryClient.Response response = client.request(HttpRegistryClient.METHOD_PUT, path, null);
        if (response.status() == HttpURLConnection.HTTP_NOT_FOUND) {
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
        String path = EurekaProtocol.PATH_APPLICATIONS_PREFIX + encode(appName(instance.serviceKey()))
                + "/" + encode(instanceId(instance));
        HttpRegistryClient.Response response = client.request(HttpRegistryClient.METHOD_DELETE, path, null);
        if (response.status() != HttpURLConnection.HTTP_NOT_FOUND) {
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
                HttpRegistryClient.METHOD_GET, EurekaProtocol.PATH_APPLICATIONS_PREFIX + encode(app), null);
        if (response.status() == HttpURLConnection.HTTP_NOT_FOUND) {
            return List.of();
        }
        requireSuccess(response);
        JsonNode application = response.json().path(EurekaProtocol.FIELD_APPLICATION);
        if (application.isMissingNode()) {
            throw new IllegalStateException("Unexpected Eureka application response shape");
        }
        JsonNode instances = application.path(EurekaProtocol.FIELD_INSTANCE);
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
        if (!EurekaProtocol.STATUS_UP.equals(source.path(EurekaProtocol.FIELD_STATUS).asText(""))) {
            return;
        }
        String overridden = source.path(EurekaProtocol.FIELD_OVERRIDDEN_STATUS).asText("");
        if (EurekaProtocol.STATUS_OUT_OF_SERVICE.equals(overridden) || EurekaProtocol.STATUS_DOWN.equals(overridden)) {
            return;
        }
        JsonNode portNode = source.path(EurekaProtocol.FIELD_PORT);
        int port = portNode.isObject()
                ? portNode.path(EurekaProtocol.FIELD_VALUE).asInt(-1) : portNode.asInt(-1);
        String host = source.path(EurekaProtocol.FIELD_IP_ADDRESS).asText("");
        if (host.isBlank()) {
            host = source.path(EurekaProtocol.FIELD_HOST_NAME).asText("");
        }
        ServiceInstance mapped = HttpRegistryIdentity.fromRemote(
                key, host, port, metadata(source.path(EurekaProtocol.FIELD_METADATA)));
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
