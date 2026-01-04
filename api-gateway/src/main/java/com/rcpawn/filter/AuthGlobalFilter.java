package com.rcpawn.filter;

import com.rcpawn.common.util.JwtUtil; // 引入刚才写的工具类
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 1. 白名单放行 (比如登录接口、注册接口)
        // 假设我们还没写登录接口，先留个口子，不然你怎么获取 Token 呢？
        if (path.contains("/auth/login") || path.contains("/doc.html")) {
            return chain.filter(exchange);
        }

        // 2. 获取 Token (通常放在 Header 的 Authorization 字段，或者 url 参数)
        // 这里为了方便测试，我们先从 Query Param 里取，生产环境建议用 Header
        String token = request.getQueryParams().getFirst("token");
        
        // 如果 url 没带，试着从 Header 取
        if (token == null) {
            String headerAuth = request.getHeaders().getFirst("Authorization");
            if (headerAuth != null && headerAuth.startsWith("Bearer ")) {
                token = headerAuth.substring(7);
            }
        }

        // 3. 校验 Token
        String userId = null;
        if (token != null) {
            userId = JwtUtil.getUserId(token); // 使用工具类解析
        }

        // 4. 校验失败 -> 401 Unauthorized
        if (userId == null) {
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.UNAUTHORIZED);

            // 1. 设置 Header，告诉浏览器返回的是 JSON
            response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");

            // 2. 构造 JSON 字符串
            String message = "{\"code\": 401, \"message\": \"非法访问，Token无效或缺失\"}";

            // 3. 写入响应体
            DataBuffer buffer = response.bufferFactory().wrap(message.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        }

        // 5. 【关键】Token 合法，把 UserID 传给下游服务
        // 这样 Consumer/Provider 就能知道是谁在访问了
        ServerHttpRequest newRequest = request.mutate()
                .header("X-User-Id", userId)
                .build();

        return chain.filter(exchange.mutate().request(newRequest).build());
    }

    @Override
    public int getOrder() {
        return 0;
    }
}