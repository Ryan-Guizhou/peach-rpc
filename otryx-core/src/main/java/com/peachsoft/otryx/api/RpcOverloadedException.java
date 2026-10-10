package com.peachsoft.otryx.api;

/**
 * RPC 资源达到并发上限时抛出的异常。
 *
 * @Author Ryan
 * @Version 1.0.0-SNAPSHOT
 * @CreateTime 2026/9/23 10:51
 */
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
