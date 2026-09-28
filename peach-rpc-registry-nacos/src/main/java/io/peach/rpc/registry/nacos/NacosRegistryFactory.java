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
        String username = options.providerOption("nacosUsername", "");
        String password = options.providerOption("nacosPassword", "");

        Properties properties = new Properties();
        properties.setProperty(
                PropertyKeyConst.SERVER_ADDR,
                String.join(",", endpoints));
        properties.setProperty(PropertyKeyConst.NAMESPACE, namespace);
        if (!username.isBlank()) {
            properties.setProperty(PropertyKeyConst.USERNAME, username);
        }
        if (!password.isBlank()) {
            properties.setProperty(PropertyKeyConst.PASSWORD, password);
        }
        try {
            NamingService namingService =
                    NacosFactory.createNamingService(properties);
            return new NacosRegistry(
                    namingService,
                    namespace,
                    group,
                    cluster);
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
