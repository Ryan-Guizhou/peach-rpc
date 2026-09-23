package io.peach.rpc.loadbalance;

import io.peach.rpc.api.ServiceInstance;

/**
 * 单次负载均衡候选实例状态。
 *
 * @param instance 服务实例
 * @param ewmaLatencyNanos 指数移动平均延迟，单位纳秒
 * @param inflight 当前进行中的请求数
 */
public record LoadBalanceContext(
        ServiceInstance instance,
        long ewmaLatencyNanos,
        int inflight) {
}
