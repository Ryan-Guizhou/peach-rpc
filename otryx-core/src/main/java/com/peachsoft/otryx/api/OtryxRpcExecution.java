package com.peachsoft.otryx.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明 Provider RPC 方法的执行资源类型。
 *
 * <p>未标注时使用 {@link RpcExecutionMode#BLOCKING_VIRTUAL}。DIRECT 只适合确定不阻塞、
 * 不等待外部资源且执行时间极短的逻辑，并且需要 Provider 显式开启。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OtryxRpcExecution {

    /**
     * 返回执行模式。
     *
     * @return Provider 执行模式
     */
    RpcExecutionMode value();
}
