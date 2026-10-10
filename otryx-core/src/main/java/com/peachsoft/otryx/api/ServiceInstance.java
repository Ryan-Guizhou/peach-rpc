package io.peach.rpc.api;

import java.util.Map;
import java.util.Objects;

/**
 * 可被 Consumer 路由的服务实例快照。
 *
 * @param instanceId 实例唯一标识
 * @param serviceKey 服务唯一键
 * @param endpoint 服务地址
 * @param weight 静态路由权重，必须大于零
 * @param metadata 扩展元数据
 */
public record ServiceInstance(
        String instanceId,
        ServiceKey serviceKey,
        RpcEndpoint endpoint,
        int weight,
        Map<String, String> metadata) {

    /** 校验并规范化服务实例。 */
    public ServiceInstance {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(serviceKey, "serviceKey");
        Objects.requireNonNull(endpoint, "endpoint");
        if (weight <= 0) {
            throw new IllegalArgumentException("weight must be positive");
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
