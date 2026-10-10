package com.peachsoft.otryx.observability;

/**
 * Registry 控制面操作类型。
 *
 * @Author Ryan
 * @Version 1.0.0
 * @CreateTime 2026/9/29 14:42
 */
public enum RpcRegistryOperation {
    /** 注册 Provider 实例。 */
    REGISTER,
    /** 注销 Provider 实例。 */
    UNREGISTER,
    /** 查询服务快照。 */
    LOOKUP,
    /** 建立服务订阅。 */
    SUBSCRIBE
}
