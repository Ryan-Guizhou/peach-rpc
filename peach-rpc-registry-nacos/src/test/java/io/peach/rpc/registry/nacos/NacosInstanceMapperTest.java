package io.peach.rpc.registry.nacos;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void shouldRoundTripWeightAndMetadata() {
        ServiceInstance source = new ServiceInstance(
                "node-1",
                KEY,
                new RpcEndpoint("127.0.0.1", 19090),
                50,
                Map.of("zone", "sg"));

        Instance nacos =
                NacosInstanceMapper.toNacos(
                        source,
                        "DEFAULT");
        ServiceInstance mapped =
                NacosInstanceMapper.fromNacos(
                        KEY,
                        nacos);

        assertEquals(0.5D, nacos.getWeight());
        assertEquals(50, mapped.weight());
        assertEquals("node-1", mapped.instanceId());
        assertEquals(
                "sg",
                mapped.metadata().get("zone"));
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
    void shouldFilterUnavailableInstances() {
        Instance instance = new Instance();
        instance.setIp("127.0.0.1");
        instance.setPort(19090);
        instance.setWeight(1D);
        instance.setHealthy(false);

        assertNull(
                NacosInstanceMapper.fromNacos(
                        KEY,
                        instance));
    }
}
