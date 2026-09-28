package io.peach.rpc.protocol;

/** RPC 协议消息类型。 */
public enum RpcMessageType {
    /** Unary 请求。 */
    REQUEST((byte) 1),
    /** Unary 响应。 */
    RESPONSE((byte) 2),
    /** 连接级心跳请求。 */
    PING((byte) 3),
    /** 连接级心跳响应。 */
    PONG((byte) 4),
    /** 服务端排空或连接关闭通知。 */
    GO_AWAY((byte) 5),
    /** 连接能力声明。 */
    HELLO((byte) 6),
    /** 连接能力协商响应。 */
    HELLO_ACK((byte) 7),
    /** 取消指定 connection-local Request ID 的调用。 */
    CANCEL((byte) 8);

    private final byte code;

    RpcMessageType(byte code) {
        this.code = code;
    }

    /**
     * 返回线协议编码。
     *
     * @return 消息类型编号
     */
    public byte code() {
        return code;
    }

    /**
     * 根据线协议编码解析消息类型。
     *
     * @param code 消息类型编号
     * @return 消息类型
     */
    public static RpcMessageType fromCode(byte code) {
        for (RpcMessageType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new RpcProtocolException("Unknown message type: " + code);
    }
}
