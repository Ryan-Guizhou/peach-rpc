package com.peachsoft.otryx.registry.eureka;

/**
 * Eureka REST 协议固定路径、字段、状态及 Adapter 专有配置键。
 *
 * <p>Eureka InstanceInfo 的大小写和特殊字段名必须与服务器契约严格一致。
 *
 * @Author OTRYX RPC Contributors
 * @Version 1.0.0
 * @CreateTime 2026/10/10 18:13
 */
final class EurekaProtocol {

    /** SPI 扩展名。 */
    static final String ADAPTER_TYPE = "eureka";
    /** 默认 REST API 地址，包含 Eureka Server context path。 */
    static final String DEFAULT_ENDPOINT = "http://127.0.0.1:8761/eureka";
    /** HTTP Basic Auth 认证 Header 与 Scheme。 */
    static final String AUTHORIZATION_HEADER = "Authorization";
    static final String BASIC_AUTH_PREFIX = "Basic ";

    /** Eureka Adapter 私有配置键。 */
    static final String OPTION_USERNAME = "eurekaUsername";
    static final String OPTION_PASSWORD = "eurekaPassword";
    static final String OPTION_LEASE_SECONDS = "eurekaLeaseSeconds";
    static final String OPTION_HEARTBEAT_SECONDS = "eurekaHeartbeatSeconds";
    static final String OPTION_POLL_INTERVAL_MILLIS = "eurekaPollIntervalMillis";
    static final String OPTION_REQUEST_TIMEOUT_MILLIS = "eurekaRequestTimeoutMillis";

    /** Application API 的相对路径前缀。 */
    static final String PATH_APPLICATIONS_PREFIX = "/apps/";

    /** Eureka 的 Application/InstanceInfo JSON 字段。 */
    static final String FIELD_APPLICATION = "application";
    static final String FIELD_INSTANCE = "instance";
    static final String FIELD_INSTANCE_ID = "instanceId";
    static final String FIELD_APP = "app";
    static final String FIELD_HOST_NAME = "hostName";
    static final String FIELD_IP_ADDRESS = "ipAddr";
    static final String FIELD_VIP_ADDRESS = "vipAddress";
    static final String FIELD_SECURE_VIP_ADDRESS = "secureVipAddress";
    static final String FIELD_STATUS = "status";
    static final String FIELD_OVERRIDDEN_STATUS = "overriddenstatus";
    static final String FIELD_PORT = "port";
    static final String FIELD_SECURE_PORT = "securePort";
    static final String FIELD_DATA_CENTER_INFO = "dataCenterInfo";
    static final String FIELD_LEASE_INFO = "leaseInfo";
    static final String FIELD_METADATA = "metadata";

    /** Eureka InstanceInfo 复合对象与 Lease 的字段。 */
    static final String FIELD_VALUE = "$";
    static final String FIELD_ENABLED = "@enabled";
    static final String FIELD_CLASS = "@class";
    static final String FIELD_NAME = "name";
    static final String FIELD_RENEWAL_INTERVAL_SECONDS = "renewalIntervalInSecs";
    static final String FIELD_LEASE_DURATION_SECONDS = "durationInSecs";

    /** Eureka 服务健康状态与覆盖状态。 */
    static final String STATUS_UP = "UP";
    static final String STATUS_DOWN = "DOWN";
    static final String STATUS_OUT_OF_SERVICE = "OUT_OF_SERVICE";

    /** Eureka REST InstanceInfo 默认数据中心与禁用 SSL Port 标记。 */
    static final String DEFAULT_DATA_CENTER_CLASS =
            "com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo";
    static final String DEFAULT_DATA_CENTER_NAME = "MyOwn";
    static final int DEFAULT_DISABLED_SECURE_PORT = 443;
    static final String PORT_ENABLED = "true";
    static final String SECURE_PORT_DISABLED = "false";

    private EurekaProtocol() {
    }
}
