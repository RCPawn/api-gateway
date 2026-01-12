package com.rcpawn.controller;

import com.rcpawn.common.util.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @Value("${server.port}") // 注入当前服务的端口号
    private String port;

    @GetMapping("/slow")
    public String slow() throws InterruptedException {
        // 故意卡顿 200ms
        Thread.sleep(200);
        return "我是慢接口，我睡了 200ms";
    }

    @GetMapping("/hello")
    public String sayHello(HttpServletRequest request) {
        // 1. 从 ThreadLocal 获取（验证拦截器是否生效）
        String userId = UserContext.getUserId();

        // 2. 从 Header 直接获取（验证 Feign 是否传了 Token）
        String tokenHeader = request.getHeader("Authorization");

        System.out.println("====== [Provider] 收到请求 ======");
        System.out.println("当前线程 UserID: " + userId);
        System.out.println("收到 Authorization 头: " + tokenHeader);

        return "Provider 响应: 你好，用户 " + userId + "，我是后端服务，我的端口是：" + port;
    }

}