package io.peach.rpc.loadbalance;

import io.peach.rpc.api.ServiceInstance;
import io.peach.rpc.spi.SPI;
import java.util.ArrayList;
import java.util.List;

/** 服务实例选择扩展点。 */
@SPI("p2c-ewma")
public interface LoadBalancer {

    /**
     * 从不可变候选数组中选择目标。
     *
     * <p>高性能实现应覆盖该方法，避免构造临时候选上下文。
     * 默认实现用于兼容只实现旧 List API 的扩展。
     *
     * @param candidates 候选服务实例
     * @param metrics 实时负载指标
     * @return 选中的服务实例
     */
    default ServiceInstance select(
            ServiceInstance[] candidates,
            LoadBalanceMetrics metrics) {
        List<LoadBalanceContext> compatibility =
                new ArrayList<>(candidates.length);
        for (ServiceInstance instance : candidates) {
            compatibility.add(new LoadBalanceContext(
                    instance,
                    metrics.ewmaLatencyNanos(instance),
                    metrics.inflight(instance)));
        }
        return select(compatibility);
    }

    /**
     * 旧版候选上下文选择接口。
     *
     * <p>保留该入口用于 0.x 兼容；新实现应优先覆盖数组入口。
     *
     * @param candidates 候选实例及运行时负载信息
     * @return 选中的服务实例
     */
    @Deprecated(forRemoval = false)
    default ServiceInstance select(
            List<LoadBalanceContext> candidates) {
        throw new UnsupportedOperationException(
                "LoadBalancer must implement an RPC candidate selection method");
    }
}
