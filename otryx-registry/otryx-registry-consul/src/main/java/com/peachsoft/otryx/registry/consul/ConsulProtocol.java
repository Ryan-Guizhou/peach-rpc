package com.peachsoft.otryx.registry.consul;

/**
 * Consul Agent 与 Health API 的协议字段、固定值及 Adapter 配置键。
 *
 * <p>仅用于 Consul Adapter 内部，集中声明区分大小写的对外协议契约。
 *
 * @Author OTRYX RPC Contributors
 * @Version 1.0.0
 * @CreateTime 2026/10/10 18:13
 */
final class ConsulProtocol {

    /** SPI 扩展名。 */
    static final String ADAPTER_TYPE = "consul";
    /** Agent HTTP API 默认入口。 */
    static final String DEFAULT_AGENT_ENDPOINT = "http://127.0.0.1:8500";
    /** ACL Token 请求头。 */
    static final String TOKEN_HEADER = "X-Consul-Token";

    /** Consul 注册中心配置键。 */
    static final String OPTION_TOKEN = "consulToken";
    static final String OPTION_TTL_SECONDS = "consulTtlSeconds";
    static final String OPTION_HEARTBEAT_SECONDS = "consulHeartbeatSeconds";
    static final String OPTION_POLL_INTERVAL_MILLIS = "consulPollIntervalMillis";
    static final String OPTION_REQUEST_TIMEOUT_MILLIS = "consulRequestTimeoutMillis";
    static final String OPTION_DATACENTER = "consulDatacenter";
    static final String OPTION_ENTERPRISE_NAMESPACE = "consulEnterpriseNamespace";

    /** Consul Agent 服务注册、注销、健康心跳与服务发现路径。 */
    static final String PATH_REGISTER_SERVICE = "/v1/agent/service/register";
    static final String PATH_DEREGISTER_SERVICE_PREFIX = "/v1/agent/service/deregister/";
    static final String PATH_PASS_CHECK_PREFIX = "/v1/agent/check/pass/";
    static final String PATH_HEALTH_SERVICE_PREFIX = "/v1/health/service/";

    /** Agent Check ID 前缀与 HTTP Query 参数。 */
    static final String SERVICE_CHECK_ID_PREFIX = "service:";
    static final String QUERY_PASSING = "?passing=true";
    static final String QUERY_DATACENTER = "&dc=";
    static final String QUERY_ENTERPRISE_NAMESPACE = "&ns=";
    static final String QUERY_AGENT_NAMESPACE = "?ns=";

    /** Service 注册请求与健康查询响应的 JSON 字段。 */
    static final String FIELD_ID = "ID";
    static final String FIELD_NAME = "Name";
    static final String FIELD_ADDRESS = "Address";
    static final String FIELD_PORT = "Port";
    static final String FIELD_META = "Meta";
    static final String FIELD_CHECK = "Check";
    static final String FIELD_SERVICE = "Service";
    static final String FIELD_NODE = "Node";
    static final String FIELD_CHECKS = "Checks";

    /** TTL Check 的 JSON 字段和固定健康状态。 */
    static final String FIELD_TTL = "TTL";
    static final String FIELD_DEREGISTER_CRITICAL_AFTER = "DeregisterCriticalServiceAfter";
    static final String FIELD_STATUS = "Status";
    static final String STATUS_CRITICAL = "critical";
    static final String STATUS_PASSING = "passing";
    static final String DEREGISTER_CRITICAL_AFTER = "1m";
    static final String SECONDS_SUFFIX = "s";

    private ConsulProtocol() {
    }
}
