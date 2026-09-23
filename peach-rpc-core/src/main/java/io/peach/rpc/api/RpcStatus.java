package io.peach.rpc.api;

/** RPC 响应状态。 */
public enum RpcStatus {
    /** 调用成功。 */
    OK((byte) 0),
    /** 请求格式或参数不合法。 */
    BAD_REQUEST((byte) 1),
    /** 服务标识不存在。 */
    SERVICE_NOT_FOUND((byte) 2),
    /** 方法标识不存在。 */
    METHOD_NOT_FOUND((byte) 3),
    /** 请求超过调用截止时间。 */
    DEADLINE_EXCEEDED((byte) 4),
    /** 服务端或传输层达到容量上限。 */
    OVERLOADED((byte) 5),
    /** 远端业务执行失败。 */
    BUSINESS_ERROR((byte) 6),
    /** 框架内部执行失败。 */
    INTERNAL_ERROR((byte) 7),
    /** 服务或端点当前不可用。 */
    UNAVAILABLE((byte) 8);

    private final byte code;

    RpcStatus(byte code) {
        this.code = code;
    }

    /**
     * 返回线协议状态编号。
     *
     * @return 状态编号
     */
    public byte code() {
        return code;
    }

    /**
     * 根据线协议编号解析状态。
     *
     * @param code 状态编号
     * @return RPC 状态
     */
    public static RpcStatus fromCode(byte code) {
        for (RpcStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown RPC status code: " + code);
    }
}
