package io.peach.rpc.registry.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import io.peach.rpc.registry.Registry;
import io.peach.rpc.registry.RegistryFactory;
import io.peach.rpc.registry.RegistryOptions;
import io.peach.rpc.spi.Extension;
import java.util.List;
import java.util.Properties;

/** Nacos 注册中心工厂。 */
@Extension("nacos")
public final class NacosRegistryFactory implements RegistryFactory {

    static final String DEFAULT_ENDPOINT = "127.0.0.1:8848";
    static final String DEFAULT_NAMESPACE = "public";
    static final String DEFAULT_GROUP = "PEACH_RPC";
    static final String DEFAULT_CLUSTER = "DEFAULT";

    /** 创建 Nacos 注册中心工厂。 */
    public NacosRegistryFactory() {
    }

    @Override
    public Registry create(RegistryOptions options) {
        List<String> endpoints = options.endpoints().isEmpty()
                ? List.of(DEFAULT_ENDPOINT)
                : options.endpoints();
        validateEndpoints(endpoints);
        String namespace = defaultIfBlank(
                options.namespace(),
                DEFAULT_NAMESPACE);
        String group = defaultIfBlank(
                options.providerOption("nacosGroup", ""),
                DEFAULT_GROUP);
        String cluster = defaultIfBlank(
                options.providerOption("nacosCluster", ""),
                DEFAULT_CLUSTER);
        validateCluster(cluster);
        Properties properties = clientProperties(
                options,
                endpoints,
                namespace);
        try {
            NamingService namingService =
                    NacosFactory.createNamingService(properties);
            return new NacosRegistry(
                    namingService,
                    namespace,
                    group,
                    cluster,
                    options.observer());
        } catch (NacosException error) {
            throw new IllegalStateException(
                    "Failed to create Nacos registry client: endpoints="
                            + endpoints
                            + ", namespace="
                            + namespace
                            + ", group="
                            + group
                            + ", cluster="
                            + cluster,
                    error);
        }
    }

    /**
     * 构造 Nacos SDK Properties。
     *
     * <p>该包级方法用于验证 Credential 只进入 SDK 配置，而不会被编码进
     * endpoint 或异常描述。
     *
     * @param options Registry 配置
     * @param endpoints 已校验端点
     * @param namespace Nacos Namespace
     * @return Nacos SDK Properties
     */
    static Properties clientProperties(
            RegistryOptions options,
            List<String> endpoints,
            String namespace) {
        Properties properties = new Properties();
        properties.setProperty(
                PropertyKeyConst.SERVER_ADDR,
                String.join(",", endpoints));
        properties.setProperty(
                PropertyKeyConst.NAMESPACE,
                namespace);
        String username =
                options.providerOption(
                        "nacosUsername",
                        "");
        String password =
                options.providerOption(
                        "nacosPassword",
                        "");
        if (!username.isBlank()) {
            properties.setProperty(
                    PropertyKeyConst.USERNAME,
                    username);
        }
        if (!password.isBlank()) {
            properties.setProperty(
                    PropertyKeyConst.PASSWORD,
                    password);
        }
        return properties;
    }

    private static void validateCluster(String cluster) {
        if (!cluster.matches("[0-9A-Za-z.-]+")) {
            throw new IllegalArgumentException(
                    "Nacos cluster must contain only 0-9, a-z, A-Z, '-' or '.'");
        }
    }

    private static void validateEndpoints(List<String> endpoints) {
        for (String endpoint : endpoints) {
            if (endpoint.indexOf('@') >= 0) {
                throw new IllegalArgumentException(
                        "Nacos endpoint must not embed credentials");
            }
        }
    }

    private static String defaultIfBlank(
            String value,
            String fallback) {
        return value == null || value.isBlank()
                ? fallback
                : value.trim();
    }
}
