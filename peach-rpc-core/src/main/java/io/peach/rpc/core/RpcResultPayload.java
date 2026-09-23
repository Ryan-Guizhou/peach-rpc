package io.peach.rpc.core;

import java.io.Serializable;

/**
 * 远程方法执行结果载荷。
 *
 * @param value 成功结果
 * @param errorType 远端错误类型
 * @param errorMessage 脱敏后的远端错误描述
 */
public record RpcResultPayload(Object value, String errorType, String errorMessage)
        implements Serializable {

    /**
     * 创建成功结果。
     *
     * @param value 返回值
     * @return 成功载荷
     */
    public static RpcResultPayload success(Object value) {
        return new RpcResultPayload(value, null, null);
    }

    /**
     * 创建失败结果。
     *
     * @param errorType 错误类型
     * @param errorMessage 错误描述
     * @return 失败载荷
     */
    public static RpcResultPayload failure(String errorType, String errorMessage) {
        return new RpcResultPayload(null, errorType, errorMessage);
    }

    /**
     * 判断远端调用是否成功。
     *
     * @return 成功时返回 true
     */
    public boolean success() {
        return errorType == null;
    }
}
