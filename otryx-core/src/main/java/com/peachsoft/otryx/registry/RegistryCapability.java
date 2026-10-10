package io.peach.rpc.registry;

/** 注册中心适配器可声明的能力。 */
public enum RegistryCapability {
    /** 支持 Provider 主动注册与注销。 */
    REGISTRATION,
    /** 支持服务变化订阅。 */
    SUBSCRIPTION,
    /** 提供可比较的单调 revision。 */
    REVISION,
    /** 支持租约或临时节点语义。 */
    LEASE,
    /** 注册中心能够提供健康状态。 */
    HEALTH,
    /** 支持服务权重。 */
    WEIGHT,
    /** 支持区域或可用区信息。 */
    ZONE,
    /** 支持集群信息。 */
    CLUSTER,
    /** 支持自定义元数据。 */
    METADATA
}
