package com.peachsoft.otryx.api;

import java.io.Serializable;

/**
 * 可在线路上传输的脱敏远端错误。
 *
 * @param errorType 远端异常类型
 * @param message 面向 Consumer 的脱敏消息
 *
 * @Author Ryan
 * @Version 1.0.0
 * @CreateTime 2026/9/23 11:46
 */
public record RpcRemoteError(String errorType, String message) implements Serializable {
}
