package com.ma.spring.framework.annotation;

import java.lang.annotation.*;

/**
 * 参数绑定注解
 *
 * @author ma
 * @date 2020/3/21 0:55
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequestParam {

    String name() default "";

    String value() default "";

}
