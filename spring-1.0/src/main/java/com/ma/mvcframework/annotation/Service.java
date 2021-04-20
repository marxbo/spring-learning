package com.ma.mvcframework.annotation;

import java.lang.annotation.*;

/**
 * 服务层注解
 *
 * @author ma
 * @date 2020/3/21 0:54
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Service {

    String value() default "";

}
