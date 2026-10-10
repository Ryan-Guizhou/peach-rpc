package io.peach.rpc.spi;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 声明框架扩展点及其默认扩展名。 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SPI {

    /**
     * 默认扩展名称。
     *
     * @return 默认扩展名称；空字符串表示无默认实现
     */
    String value() default "";
}
