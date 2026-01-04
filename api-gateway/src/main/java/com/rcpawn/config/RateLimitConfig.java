package com.rcpawn.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary; // 导入这个包
import reactor.core.publisher.Mono;
import java.util.Objects;

@Configuration
public class RateLimitConfig {

    /**
     * 按 URL 限流
     */
    @Bean
    @Primary  // ✅ 加在这里！告诉 Spring 这是默认的首选 Bean
    public KeyResolver pathKeyResolver() {
        return exchange -> Mono.just(
                Objects.requireNonNull(exchange.getRequest().getURI().getPath())
        );
    }

    /**
     * 按 IP 限流
     */
    @Bean
    // 这里不用加 @Primary，因为它只是备选
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(
                Objects.requireNonNull(exchange.getRequest().getRemoteAddress()).getAddress().getHostAddress()
        );
    }
}