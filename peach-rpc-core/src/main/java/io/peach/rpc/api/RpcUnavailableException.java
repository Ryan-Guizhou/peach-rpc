package io.peach.rpc.api;

/** RPC 服务或传输端点当前不可用时抛出的异常。 */
public final class RpcUnavailableException extends RpcException {

    /**
     * 创建不可用异常。
     *
     * @param message 错误描述
     */
    public RpcUnavailableException(String message) {
        super(message);
    }

    /**
     * 创建携带原始原因的不可用异常。
     *
     * @param message 错误描述
     * @param cause 原始异常
     */
    public RpcUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
