package com.peachsoft.otryx.api;

/**
 * RPC 请求超过 Deadline 时抛出的异常。
 *
 * @Author Ryan
 * @Version 1.0.0
 * @CreateTime 2026/9/23 10:51
 */
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
