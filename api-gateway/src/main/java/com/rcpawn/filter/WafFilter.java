package com.rcpawn.filter;

import com.rcpawn.util.LogBuffer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class WafFilter implements GlobalFilter, Ordered {

    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private LogBuffer logBuffer;

    public static final String WAF_ENABLE_KEY = "gateway:waf:enable";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1. 检查开关是否开启
        String enable = redisTemplate.opsForValue().get(WAF_ENABLE_KEY);
        if ("true".equals(enable)) {
            // WAF 开启模式：这里简单演示“拦截所有”或“特定IP”
            // 实际生产中这里会去查 "gateway:waf:blacklist"
            String ip = exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
            
            // 模拟拦截：假设我们要拦截 192.168.x.x
            // 为了演示效果，你可以改成拦截本机 IP
            if (ip.startsWith("192.168.0.100")) { 
                logBuffer.record(ip, "WAF", "IP Blocked by Firewall");
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() { return -100; } // 优先级最高，最先拦截
}