package com.peachsoft.otryx.loadbalance;

import com.peachsoft.otryx.api.ServiceInstance;

/**
 * 单次负载均衡候选实例状态。
 *
 * @param instance 服务实例
 * @param ewmaLatencyNanos 指数移动平均延迟，单位纳秒
 * @param inflight 当前进行中的请求数
 *
 * @Author Ryan
 * @Version 1.0.0-SNAPSHOT
 * @CreateTime 2026/9/23 10:51
 */
public record LoadBalanceContext(
        ServiceInstance instance,
        long ewmaLatencyNanos,
        int inflight) {
}
