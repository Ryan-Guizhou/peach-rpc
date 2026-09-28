package io.peach.rpc.api;

import java.util.Objects;

/** 携带远端 RPC 状态与脱敏异常类型的调用异常。 */
public final class RpcRemoteException extends RpcException {

    /** 远端返回的 RPC 状态。 */
    private final RpcStatus status;
    /** 远端脱敏后的异常类型。 */
    private final String remoteErrorType;

    /**
     * 创建远端 RPC 异常。
     *
     * @param status RPC 状态
     * @param remoteErrorType 远端脱敏异常类型
     * @param message 错误描述
     */
    public RpcRemoteException(
            RpcStatus status,
            String remoteErrorType,
            String message) {
        super(message);
        this.status = Objects.requireNonNull(status, "status");
        this.remoteErrorType = Objects.requireNonNull(
                remoteErrorType,
                "remoteErrorType");
    }

    /**
     * 返回 RPC 状态。
     *
     * @return RPC 状态
     */
    public RpcStatus status() {
        return status;
    }

    /**
     * 返回远端脱敏异常类型。
     *
     * @return 远端脱敏异常类型
     */
    public String remoteErrorType() {
        return remoteErrorType;
    }
}
