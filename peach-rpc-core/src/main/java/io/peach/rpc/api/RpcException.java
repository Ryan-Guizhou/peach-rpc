package io.peach.rpc.api;

/** RPC 基础运行时异常。 */
public class RpcException extends RuntimeException {

    /**
     * 创建 RPC 异常。
     *
     * @param message 错误描述
     */
    public RpcException(String message) {
        super(message);
    }

    /**
     * 创建携带原始原因的 RPC 异常。
     *
     * @param message 错误描述
     * @param cause 原始异常
     */
    public RpcException(String message, Throwable cause) {
        super(message, cause);
    }
}
