package io.peach.rpc.observability;

/** RPC 连接关闭或摘除原因。 */
public enum RpcConnectionCloseReason {
    /** 本地主动正常关闭。 */
    LOCAL_CLOSE,
    /** 收到 GO_AWAY 后进入排空。 */
    GO_AWAY,
    /** Heartbeat 超时判定连接不可用。 */
    HEARTBEAT_TIMEOUT,
    /** TCP/网络异常。 */
    TRANSPORT_ERROR,
    /** 协议或握手错误。 */
    PROTOCOL_ERROR,
    /** 远端关闭连接。 */
    REMOTE_CLOSE
}
