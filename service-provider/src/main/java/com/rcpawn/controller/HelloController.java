package com.rcpawn.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @Value("${server.port}") // 注入当前服务的端口号
    private String port;

    @GetMapping("/hello")
    public String sayHello() {
        return "我是后端服务，我的端口是：" + port;
    }

}