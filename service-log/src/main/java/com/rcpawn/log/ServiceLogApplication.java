package com.rcpawn.log;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
// 扫描 Mapper 接口的位置 (一定要指向你的 mapper 包)
@MapperScan("com.rcpawn.log.mapper") 
public class ServiceLogApplication {
    public static void main(String[] args) {
        SpringApplication.run(ServiceLogApplication.class, args);
    }
}