package com.rcpawn.log;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.rcpawn.log.mapper") // 扫描 Mapper 接口的位置
@EnableScheduling // 👈 开启定时任务开关
public class ServiceLogApplication {
    public static void main(String[] args) {
        SpringApplication.run(ServiceLogApplication.class, args);
    }
}