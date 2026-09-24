package io.peach.rpc.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记允许框架在基础设施瞬时失败时自动重试的方法。
 *
 * <p>调用方必须保证相同参数重复执行不会产生不可接受的重复副作用。
 * 未标记的方法即使配置了重试次数也不会进行自动重试。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PeachRpcIdempotent {
}
