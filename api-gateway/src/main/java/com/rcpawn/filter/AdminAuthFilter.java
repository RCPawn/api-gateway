package com.rcpawn.filter;

import com.rcpawn.common.util.JwtUtil;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 网关自有接口安全过滤器
 * 专门保护 /admin/** 等本地接口
 */
//@Component
public class AdminAuthFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 1. 只拦截 /admin/** 开头的请求 (路由管理接口)
        if (!path.startsWith("/admin")) {
            return chain.filter(exchange); // 不是管理接口，放行（交给后面的 AuthGlobalFilter 去管转发）
        }

        // 2. 简单的 Token 校验 (逻辑和 AuthGlobalFilter 类似)
        // 这里简化演示，只检查 Authorization 头
        String token = null;
        String headerAuth = request.getHeaders().getFirst("Authorization");
        if (headerAuth != null && headerAuth.startsWith("Bearer ")) {
            token = headerAuth.substring(7);
        }

        // 3. 校验
        String userId = null;
        if (token != null) {
            try {
                userId = JwtUtil.getUserId(token);
            } catch (Exception e) {
                // 解析失败
            }
        }

        // 4. 失败拦截
        if (userId == null) {
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            response.getHeaders().add("Content-Type", "application/json");
            String msg = "{\"code\": 401, \"msg\": \"网关管理接口拒绝访问\"}";
            DataBuffer buffer = response.bufferFactory().wrap(msg.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        }

        // 5. 成功放行
        return chain.filter(exchange);
    }
}