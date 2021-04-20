package com.ma.mvcframework.annotation;

import java.lang.annotation.*;

/**
 * 注入注解
 *
 * @author ma
 * @date 2020/3/21 0:51
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Autowired {

    /**
     * 自定义注解只有一个属性时，且属性名为value时，赋值时value可省略。
     */
    String value() default "";

    boolean required() default true;

}
