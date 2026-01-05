package com.rcpawn.controller;

import com.rcpawn.common.util.UserContext;
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
        // 1. 打印 Consumer 这一层收到的数据
        String currentUserId = UserContext.getUserId();
        System.out.println("====== [Consumer] 开始调用 ======");
        System.out.println("Consumer 当前 UserID: " + currentUserId);

        // 2. 调用 Provider (此时 Feign 拦截器会工作)
        String result = providerClient.hello(); // 调用上面 Provider 的 /hello 接口

        return "Consumer 调用结果 -> " + result;
    }
}