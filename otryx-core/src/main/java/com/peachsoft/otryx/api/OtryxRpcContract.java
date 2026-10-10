package io.peach.rpc.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记可在编译期生成 Peach RPC Stub 的服务接口。
 *
 * <p>注解本身不会改变运行时语义；未生成 Stub 时仍可回退到运行时代理。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface PeachRpcContract {
}
