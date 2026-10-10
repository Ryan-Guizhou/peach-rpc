package com.peachsoft.otryx.protocol;

/**
 * RPC 线协议格式、长度或版本不合法时抛出的异常。
 *
 * @Author Ryan
 * @Version 1.0.0
 * @CreateTime 2026/9/23 10:51
 */
public final class RpcProtocolException extends RuntimeException {

    /**
     * 创建协议异常。
     *
     * @param message 错误描述
     */
    public RpcProtocolException(String message) {
        super(message);
    }

    /**
     * 创建携带原始原因的协议异常。
     *
     * @param message 错误描述
     * @param cause 原始异常
     */
    public RpcProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
