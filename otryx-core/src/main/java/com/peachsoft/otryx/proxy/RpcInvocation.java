package com.peachsoft.otryx.proxy;

import java.lang.reflect.Method;
import java.util.concurrent.CompletionStage;

/**
 * Proxy 到 Core 的统一调用函数。
 *
 * @Author Ryan
 * @Version 1.0.0
 * @CreateTime 2026/9/23 10:51
 */
@FunctionalInterface
public interface RpcInvocation {

    /**
     * 发起一次 RPC 调用。
     *
     * @param method Java 服务方法
     * @param args 调用参数
     * @return 异步调用结果
     */
    CompletionStage<Object> invoke(Method method, Object[] args);
}
