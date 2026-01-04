package com.rcpawn.service;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

// value = Nacos 里注册的服务名 (被调用方的名字)
@FeignClient(value = "service-provider") 
public interface ProviderClient {

    // 这里的路径必须和 service-provider Controller 里的路径完全一致
    @GetMapping("/hello")
    String callHello(); 
}