package io.peach.rpc.loadbalance.defaults;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.peach.rpc.api.RpcEndpoint;
import io.peach.rpc.api.ServiceInstance;
import io.peach.rpc.api.ServiceKey;
import io.peach.rpc.loadbalance.LoadBalanceContext;
import io.peach.rpc.loadbalance.LoadBalanceMetrics;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class P2cEwmaLoadBalancerTest {
    private final P2cEwmaLoadBalancer loadBalancer = new P2cEwmaLoadBalancer();

    @Test
    void shouldReturnNullWhenNoCandidateExists() {
        assertNull(loadBalancer.select(List.of()));
    }

    @Test
    void shouldReturnTheOnlyCandidate() {
        ServiceInstance instance = instance("node-1", 19090);
        assertEquals(instance, loadBalancer.select(List.of(new LoadBalanceContext(instance, 1_000_000, 0))));
    }


    @Test
    void shouldSelectFromArrayWithoutCompatibilityContexts() {
        ServiceInstance first = instance("node-1", 19090);
        ServiceInstance second = instance("node-2", 19091);
        LoadBalanceMetrics metrics = new LoadBalanceMetrics() {
            @Override
            public long ewmaLatencyNanos(ServiceInstance instance) {
                return instance == first ? 1_000_000L : 100_000_000L;
            }

            @Override
            public int inflight(ServiceInstance instance) {
                return instance == first ? 0 : 100;
            }
        };

        for (int index = 0; index < 20; index++) {
            assertEquals(
                    first,
                    loadBalancer.select(
                            new ServiceInstance[] {first, second},
                            metrics));
        }
    }

    @Test
    void shouldSkipUnavailableOutlier() {
        ServiceInstance first = instance("node-1", 19090);
        ServiceInstance second = instance("node-2", 19091);
        LoadBalanceMetrics metrics = new LoadBalanceMetrics() {
            @Override
            public long ewmaLatencyNanos(ServiceInstance instance) {
                return 1_000_000L;
            }

            @Override
            public int inflight(ServiceInstance instance) {
                return 0;
            }

            @Override
            public boolean available(ServiceInstance instance) {
                return instance == second;
            }
        };

        for (int index = 0; index < 20; index++) {
            assertEquals(
                    second,
                    loadBalancer.select(
                            new ServiceInstance[] {first, second},
                            metrics));
        }
    }

    private static ServiceInstance instance(String id, int port) {
        return new ServiceInstance(
                id,
                new ServiceKey("demo.Service", "1.0.0", "default"),
                new RpcEndpoint("127.0.0.1", port),
                100,
                Map.of());
    }
}
