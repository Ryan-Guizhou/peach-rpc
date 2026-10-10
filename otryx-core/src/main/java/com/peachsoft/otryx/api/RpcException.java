package com.peachsoft.otryx.api;

/**
 * RPC 基础运行时异常。
 *
 * @Author Ryan
 * @Version 1.0.0-SNAPSHOT
 * @CreateTime 2026/9/23 10:51
 */
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
