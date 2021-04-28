package com.ma.spring.framework.context;

import com.ma.spring.framework.beans.config.BeanDefinition;
import com.ma.spring.framework.beans.support.BeanDefinitionReader;
import com.ma.spring.framework.beans.support.DefaultListableBeanFactory;
import com.ma.spring.framework.core.BeanFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 *
 * @author ma
 * @date 2021/4/26 22:55
 */
public class ApplicationContext implements BeanFactory {

    public Map<String, BeanDefinition> beanDefinitionMap = new HashMap<>();

    private DefaultListableBeanFactory registry = new DefaultListableBeanFactory();

    private BeanDefinitionReader reader;

    public ApplicationContext(String... configLocations) {
        // 1、加载配置文件
        reader = new BeanDefinitionReader(configLocations);

        // 2、解析配置文件，封装成BeanDefinition
        List<BeanDefinition> beanDefinitions = reader.loadBeanDefinitions();

        // 3、将所有的配置信息缓存起来
        this.registry.doRegistBeanDefinition(beanDefinitions);

        // 4、加载非延时加载的所有Bean
        doLoadInstance();
    }

    private void doLoadInstance() {
        // 循环调用getBean()方法


    }

    @Override
    public Object getBean(Class beanClass) {
        return null;
    }

    @Override
    public Object getBean(String beanName) {
        return null;
    }
}
