package com.peachsoft.otryx.observability;

/**
 * RPC 连接在本地进程中的角色。
 *
 * @Author Ryan
 * @Version 1.0.0
 * @CreateTime 2026/9/29 13:43
 */
public enum RpcConnectionRole {
    /** Consumer 主动建立的出站连接。 */
    CLIENT,
    /** Provider 接受的入站连接。 */
    SERVER
}
