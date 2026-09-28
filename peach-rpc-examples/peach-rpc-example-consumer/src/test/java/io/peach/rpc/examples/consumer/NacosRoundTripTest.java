package io.peach.rpc.examples.consumer;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.peach.rpc.examples.api.GreetingService;
import io.peach.rpc.examples.provider.ProviderApplication;
import io.peach.rpc.generated.RpcGeneratedClients;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** 两个独立 Spring Context 通过 Nacos 完成真实 RPC round-trip。 */
class NacosRoundTripTest {

    @Test
    void providerAndConsumerShouldCommunicateThroughNacos() {
        String endpoint = System.getenv(
                "NACOS_TEST_ENDPOINT");
        assumeTrue(
                endpoint != null && !endpoint.isBlank(),
                "NACOS_TEST_ENDPOINT is required");

        String group = "PEACH_RPC_E2E_"
                + UUID.randomUUID()
                        .toString()
                        .replace("-", "");
        try (ConfigurableApplicationContext provider =
                     new SpringApplicationBuilder(
                             ProviderApplication.class)
                             .web(WebApplicationType.NONE)
                             .properties(
                                     registryProperties(
                                             endpoint,
                                             group))
                             .properties(
                                     "peach.rpc.server.host=127.0.0.1",
                                     "peach.rpc.server.port=0",
                                     "peach.rpc.server.advertised-host=127.0.0.1",
                                     "peach.rpc.server.advertised-port=0")
                             .run();
             ConfigurableApplicationContext consumer =
                     new SpringApplicationBuilder(
                             ConsumerApplication.class)
                             .web(WebApplicationType.NONE)
                             .properties(
                                     registryProperties(
                                             endpoint,
                                             group))
                             .run()) {
            assertTrue(
                    RpcGeneratedClients.find(
                                    GreetingService.class)
                            .isPresent());
            assertTrue(provider.isActive());
            assertTrue(consumer.isActive());
        }
    }

    private static String[] registryProperties(
            String endpoint,
            String group) {
        return new String[]{
                "peach.rpc.registry.type=nacos",
                "peach.rpc.registry.endpoints=" + endpoint,
                "peach.rpc.registry.namespace=public",
                "peach.rpc.registry.nacos.group=" + group,
                "peach.rpc.registry.nacos.cluster=DEFAULT"
        };
    }
}
