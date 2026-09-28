package io.peach.rpc.registry.nacos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.alibaba.nacos.api.naming.pojo.Instance;
import io.peach.rpc.api.RpcEndpoint;
import io.peach.rpc.api.ServiceInstance;
import io.peach.rpc.api.ServiceKey;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NacosInstanceMapperTest {

    private static final ServiceKey KEY =
            new ServiceKey(
                    "demo.GreetingService",
                    "1.0.0",
                    "default");

    @Test
    void shouldRoundTripDefaultWeightAndMetadata() {
        assertRoundTripWeight(100, 1.0D);
    }

    @Test
    void shouldRoundTripHalfWeightAndMetadata() {
        assertRoundTripWeight(50, 0.5D);
    }

    @Test
    void shouldRoundTripDoubleWeightAndMetadata() {
        assertRoundTripWeight(200, 2.0D);
    }

    @Test
    void shouldRejectReservedMetadata() {
        ServiceInstance source = new ServiceInstance(
                "node-1",
                KEY,
                new RpcEndpoint("127.0.0.1", 19090),
                100,
                Map.of(
                        NacosReservedMetadata.INSTANCE_ID,
                        "override"));

        assertThrows(
                IllegalArgumentException.class,
                () -> NacosInstanceMapper.toNacos(
                        source,
                        "DEFAULT"));
    }

    @Test
    void shouldGenerateFallbackInstanceId() {
        Instance instance = healthyInstance();
        instance.setMetadata(Map.of());

        ServiceInstance mapped =
                NacosInstanceMapper.fromNacos(
                        KEY,
                        instance);

        assertNotNull(mapped);
        assertEquals(
                "demo.GreetingService:1.0.0:default"
                        + "@DEFAULT@127.0.0.1:19090",
                mapped.instanceId());
    }

    @Test
    void shouldFilterUnhealthyInstance() {
        Instance instance = healthyInstance();
        instance.setHealthy(false);

        assertNull(
                NacosInstanceMapper.fromNacos(
                        KEY,
                        instance));
    }

    @Test
    void shouldFilterDisabledInstance() {
        Instance instance = healthyInstance();
        instance.setEnabled(false);

        assertNull(
                NacosInstanceMapper.fromNacos(
                        KEY,
                        instance));
    }

    @Test
    void shouldFilterZeroWeightInstance() {
        Instance instance = healthyInstance();
        instance.setWeight(0D);

        assertNull(
                NacosInstanceMapper.fromNacos(
                        KEY,
                        instance));
    }

    @Test
    void shouldFilterInvalidPortInstance() {
        Instance instance = healthyInstance();
        instance.setPort(0);

        assertNull(
                NacosInstanceMapper.fromNacos(
                        KEY,
                        instance));
    }

    @Test
    void shouldRejectWildcardOutboundHost() {
        ServiceInstance source = new ServiceInstance(
                "node-1",
                KEY,
                new RpcEndpoint("0.0.0.0", 19090),
                100,
                Map.of());

        assertThrows(
                IllegalArgumentException.class,
                () -> NacosInstanceMapper.toNacos(
                        source,
                        "DEFAULT"));
    }

    @Test
    void shouldFilterWildcardInboundHost() {
        Instance instance = healthyInstance();
        instance.setIp("0.0.0.0");

        assertNull(
                NacosInstanceMapper.fromNacos(
                        KEY,
                        instance));
    }

    @Test
    void shouldRejectInvalidOutboundPort() {
        ServiceInstance source = new ServiceInstance(
                "node-1",
                KEY,
                new RpcEndpoint("127.0.0.1", 0),
                100,
                Map.of());

        assertThrows(
                IllegalArgumentException.class,
                () -> NacosInstanceMapper.toNacos(
                        source,
                        "DEFAULT"));
    }

    private static void assertRoundTripWeight(
            int coreWeight,
            double nacosWeight) {
        ServiceInstance source = new ServiceInstance(
                "node-1",
                KEY,
                new RpcEndpoint("127.0.0.1", 19090),
                coreWeight,
                Map.of("zone", "sg"));

        Instance nacos =
                NacosInstanceMapper.toNacos(
                        source,
                        "DEFAULT");
        ServiceInstance mapped =
                NacosInstanceMapper.fromNacos(
                        KEY,
                        nacos);

        assertEquals(nacosWeight, nacos.getWeight());
        assertEquals(coreWeight, mapped.weight());
        assertEquals("node-1", mapped.instanceId());
        assertEquals(
                "sg",
                mapped.metadata().get("zone"));
        assertEquals(
                "demo.GreetingService",
                mapped.metadata().get(
                        NacosReservedMetadata.INTERFACE));
        assertEquals(
                "peach-rpc",
                mapped.metadata().get(
                        NacosReservedMetadata.PROTOCOL));
    }

    private static Instance healthyInstance() {
        Instance instance = new Instance();
        instance.setIp("127.0.0.1");
        instance.setPort(19090);
        instance.setWeight(1D);
        instance.setHealthy(true);
        instance.setEnabled(true);
        instance.setClusterName("DEFAULT");
        return instance;
    }
}
