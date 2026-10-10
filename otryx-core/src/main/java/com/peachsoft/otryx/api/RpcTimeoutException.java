package io.peach.rpc.api;

/** RPC 请求超过 Deadline 时抛出的异常。 */
public final class RpcTimeoutException extends RpcException {

    /**
     * 创建超时异常。
     *
     * @param message 错误描述
     */
    public RpcTimeoutException(String message) {
        super(message);
    }
}
