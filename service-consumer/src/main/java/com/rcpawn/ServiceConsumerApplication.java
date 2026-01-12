package com.rcpawn;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;

// 👇 加上 exclude 参数，告诉它：我有数据库的包，但我不用，别给我自动配置！
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@EnableFeignClients // 👈 这一行是开关，必须加！
public class ServiceConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceConsumerApplication.class, args);
    }

}
