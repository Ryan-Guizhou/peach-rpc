package io.peach.rpc.loadbalance;

import io.peach.rpc.api.ServiceInstance;

/**
 * 负载均衡器读取的实时端点指标。
 *
 * <p>实现必须保证读取足够轻量，不应在单次查询中分配临时对象。
 */
public interface LoadBalanceMetrics {

    /**
     * 返回实例 EWMA 延迟。
     *
     * @param instance 服务实例
     * @return EWMA 延迟，单位纳秒
     */
    long ewmaLatencyNanos(ServiceInstance instance);

    /**
     * 返回实例当前 inflight。
     *
     * @param instance 服务实例
     * @return 当前未完成请求数
     */
    int inflight(ServiceInstance instance);
}
