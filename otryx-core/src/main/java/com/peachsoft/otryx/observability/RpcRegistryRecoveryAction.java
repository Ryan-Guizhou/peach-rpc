package com.peachsoft.otryx.observability;

/**
 * Registry 控制面恢复动作。
 *
 * @Author Ryan
 * @Version 1.0.0
 * @CreateTime 2026/9/29 14:42
 */
public enum RpcRegistryRecoveryAction {
    /** Lease/临时实例恢复。 */
    REGISTRATION_RECOVERED,
    /** Watch/订阅恢复。 */
    SUBSCRIPTION_RECOVERED,
    /** 控制面重新同步当前快照。 */
    SNAPSHOT_RESYNCED
}
