package io.peach.rpc.observability;

/** RPC 连接在本地进程中的角色。 */
public enum RpcConnectionRole {
    /** Consumer 主动建立的出站连接。 */
    CLIENT,
    /** Provider 接受的入站连接。 */
    SERVER
}
