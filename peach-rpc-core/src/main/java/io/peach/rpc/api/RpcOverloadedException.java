package io.peach.rpc.api;

/** RPC 资源达到并发上限时抛出的异常。 */
public final class RpcOverloadedException extends RpcException {

    /**
     * 创建过载异常。
     *
     * @param message 错误描述
     */
    public RpcOverloadedException(String message) {
        super(message);
    }
}
