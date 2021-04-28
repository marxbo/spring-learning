package com.ma.spring.framework.annotation;

import java.lang.annotation.*;

/**
 * 映射路径注解
 *
 * @author ma
 * @date 2020/3/21 0:55
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequestMapping {

    String value() default "";

}
