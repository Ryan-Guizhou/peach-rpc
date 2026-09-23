package io.peach.rpc.loadbalance.defaults;

import io.peach.rpc.api.ServiceInstance;
import io.peach.rpc.loadbalance.LoadBalanceContext;
import io.peach.rpc.loadbalance.LoadBalanceMetrics;
import io.peach.rpc.loadbalance.LoadBalancer;
import io.peach.rpc.spi.Extension;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Power-of-Two-Choices + EWMA 负载均衡实现。 */
@Extension("p2c-ewma")
public final class P2cEwmaLoadBalancer implements LoadBalancer {

    /** 创建 P2C + EWMA 负载均衡器。 */
    public P2cEwmaLoadBalancer() {
    }

    @Override
    public ServiceInstance select(
            ServiceInstance[] candidates,
            LoadBalanceMetrics metrics) {
        int size = candidates.length;
        if (size == 0) {
            return null;
        }
        if (size == 1) {
            return candidates[0];
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        int firstIndex = random.nextInt(size);
        int secondIndex = random.nextInt(size - 1);
        if (secondIndex >= firstIndex) {
            secondIndex++;
        }

        ServiceInstance first = candidates[firstIndex];
        ServiceInstance second = candidates[secondIndex];
        return score(first, metrics) <= score(second, metrics)
                ? first
                : second;
    }

    @Override
    public ServiceInstance select(
            List<LoadBalanceContext> candidates) {
        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == 1) {
            return candidates.getFirst().instance();
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        int firstIndex = random.nextInt(candidates.size());
        int secondIndex = random.nextInt(candidates.size() - 1);
        if (secondIndex >= firstIndex) {
            secondIndex++;
        }

        LoadBalanceContext first = candidates.get(firstIndex);
        LoadBalanceContext second = candidates.get(secondIndex);
        return score(first) <= score(second)
                ? first.instance()
                : second.instance();
    }

    private static double score(
            ServiceInstance instance,
            LoadBalanceMetrics metrics) {
        long latencyNanos = Math.max(
                metrics.ewmaLatencyNanos(instance),
                1_000_000L);
        return latencyNanos
                * (metrics.inflight(instance) + 1.0)
                / instance.weight();
    }

    private static double score(
            LoadBalanceContext context) {
        long latencyNanos = Math.max(
                context.ewmaLatencyNanos(),
                1_000_000L);
        return latencyNanos
                * (context.inflight() + 1.0)
                / context.instance().weight();
    }
}
