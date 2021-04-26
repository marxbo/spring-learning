package com.ma.demo.service.impl;

import com.ma.demo.service.DemoService;
import com.ma.mvcframework.annotation.Service;

/**
 * Service实现类
 *
 * @author ma
 * @date 2020/4/5 12:08
 */
@Service
public class DemoServiceImpl implements DemoService {

    @Override
    public String get(String name) {
        return "My name is " + name;
    }

}
