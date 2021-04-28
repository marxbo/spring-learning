package com.ma.spring.framework.beans.support;

import com.ma.spring.framework.beans.config.BeanDefinition;
import com.ma.spring.framework.core.BeanFactory;

import java.util.List;

/**
 *
 *
 * @author ma
 * @date 2021/4/26 22:53
 */
public class DefaultListableBeanFactory implements BeanFactory {

    @Override
    public Object getBean(Class beanClass) {
        return null;
    }

    @Override
    public Object getBean(String beanName) {
        return null;
    }

    public void doRegistBeanDefinition(List<BeanDefinition> beanDefinitions) {

    }
}
