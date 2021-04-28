package com.ma.spring.framework.core;

/**
 * 创建对象工厂最顶层的接口
 *
 * @author ma
 * @date 2021/4/26 22:47
 */
public interface BeanFactory {

    Object getBean(Class beanClass);

    Object getBean(String beanName);

}
