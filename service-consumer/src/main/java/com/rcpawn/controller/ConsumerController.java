package com.rcpawn.controller;

import com.rcpawn.service.ProviderClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/consumer")
public class ConsumerController {

    @Autowired
    private ProviderClient providerClient; // 注入接口

    @GetMapping("/test")
    public String test() {
        // 像调用本地方法一样调用远程服务
        String result = providerClient.callHello(); 
        return "我是消费者，远程服务返回了：" + result;
    }
}