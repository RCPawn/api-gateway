package com.rcpawn;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;


// 👇 加上 exclude 参数，告诉它：我有数据库的包，但我不用，别给我自动配置！
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }

}
