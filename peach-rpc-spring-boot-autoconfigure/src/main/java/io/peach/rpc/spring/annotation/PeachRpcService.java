package io.peach.rpc.spring.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要由 Peach RPC 暴露的 Spring Bean。
 *
 * <p>服务实现 Bean 会在 Spring 容器完成创建后注册到 Peach RPC Provider。
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PeachRpcService {

    /**
     * RPC 服务接口。
     *
     * <p>未显式指定时，框架要求实现类只实现一个业务接口并自动推断。
     *
     * @return RPC 服务接口
     */
    Class<?> interfaceClass() default void.class;

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
