package com.peachsoft.otryx.protocol;

/**
 * 连接握手可协商的协议能力。
 *
 * @Author Ryan
 * @Version 1.0.0
 * @CreateTime 2026/9/23 11:46
 */
public enum RpcFeature {
    /** Deadline 元数据。 */
    DEADLINE(1),
    /** 调用取消传播。 */
    CANCEL(1 << 1),
    /** 流式 RPC。 */
    STREAMING(1 << 2),
    /** GO_AWAY 优雅排空。 */
    GO_AWAY(1 << 3),
    /** 应用层 PING/PONG 心跳。 */
    HEARTBEAT(1 << 4);

    private final int mask;

    RpcFeature(int mask) {
        this.mask = mask;
    }

    /**
     * 返回线协议位掩码。
     *
     * @return 位掩码
     */
    public int mask() {
        return mask;
    }
}
