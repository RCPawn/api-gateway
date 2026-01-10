package com.rcpawn.filter;

import com.rcpawn.util.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
public class ReplayAttackFilter implements GlobalFilter, Ordered {

    @Autowired
    private RedisUtil redisUtil;

    // 限制请求时间必须在 5 分钟内
    private static final long MAX_REQUEST_TIME = 5 * 60 * 1000;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 1. 白名单放行 (增加监控端点 actuator)
        if (path.contains("/doc.html") ||
                path.contains("/v3/api-docs") ||
                path.contains("/admin/routes") || // 路由管理接口也不需要防重放(或者是为了方便调试)
                path.contains("/actuator")) {     // 监控端点必须放行！
            return chain.filter(exchange);
        }

        // 2. 获取 Header 中的 Timestamp 和 Nonce
        String timestamp = exchange.getRequest().getHeaders().getFirst("Timestamp");
        String nonce = exchange.getRequest().getHeaders().getFirst("Nonce");

        // 简单校验非空
        if (!StringUtils.hasText(timestamp) || !StringUtils.hasText(nonce)) {
            return errorResponse(exchange, "缺少安全头(Timestamp/Nonce)");
        }

        // 3. 校验时间戳 (防止太旧的请求)
        long requestTime;
        try {
            requestTime = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            return errorResponse(exchange, "时间戳格式错误");
        }

        long now = System.currentTimeMillis();
        // 如果请求时间比现在还晚(未来时间)，或者请求太老(超过5分钟)
        if (now - requestTime > MAX_REQUEST_TIME || requestTime > now + 10000) { // 宽容10秒误差
            return errorResponse(exchange, "请求已过期，拒绝访问");
        }

        // 4. 校验 Nonce (核心：Redis 查重)
        // 逻辑：尝试把 nonce 存入 Redis，有效期 5 分钟。
        // 如果 Redis 里已经有了，说明是重复请求。
        // 注意：这里用 setIfAbsent (SETNX) 逻辑最好，但简单起见我们先查再存
        
        return redisUtil.hasKey(nonce)
                .flatMap(exists -> {
                    if (exists) {
                        return errorResponse(exchange, "检测到重放攻击(Nonce重复)");
                    } else {
                        // 存入 Redis，过期时间要 >= MAX_REQUEST_TIME (这里设为 300秒)
                        // 必须 subscribe() 订阅才能执行保存操作，或者使用 flatMap 连接流
                        return redisUtil.set(nonce, "1", 300)
                                .flatMap(success -> chain.filter(exchange));
                    }
                });
    }

    /**
     * 返回错误 JSON
     */
    private Mono<Void> errorResponse(ServerWebExchange exchange, String msg) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN); // 403
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        String json = "{\"code\": 403, \"msg\": \"" + msg + "\"}";
        DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -1; // 优先级高一点，排在鉴权之前或之后都可以
    }
}