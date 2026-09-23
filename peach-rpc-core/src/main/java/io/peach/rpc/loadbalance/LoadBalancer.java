package io.peach.rpc.loadbalance;

import io.peach.rpc.api.ServiceInstance;
import io.peach.rpc.spi.SPI;
import java.util.List;

/** 服务实例选择扩展点。 */
@SPI("p2c-ewma")
public interface LoadBalancer {

    /**
     * 从候选实例中选择一个目标。
     *
     * @param candidates 候选实例及运行时负载信息
     * @return 选中的服务实例
     */
    ServiceInstance select(List<LoadBalanceContext> candidates);
}
