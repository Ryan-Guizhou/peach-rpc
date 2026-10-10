package io.peach.rpc.spring.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要注入 Peach RPC Consumer 代理的字段。
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PeachRpcReference {

    /**
     * 服务版本。
     *
     * @return 服务版本
     */
    String version() default "";

    /**
     * 服务分组。
     *
     * @return 服务分组
     */
    String group() default "default";
}
