package com.rcpawn.filter;

import com.rcpawn.common.util.JwtUtil;
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

        // 1. 白名单放行
        // 必须放行 /v3/api-docs，否则文档数据加载不出来
        if (path.contains("/auth/login") ||
                path.contains("/doc.html") ||
                path.contains("/v3/api-docs") ||  // <--- 新增这行
                path.contains("/webjars") ||      // <--- 新增这行(静态资源)
                path.contains("/favicon.ico")) {  // <--- 新增这行(图标)
            return chain.filter(exchange);
        }

        // 2. 获取 Token
        // 优先从 Header 取（生产环境标准），其次从 URL 取（方便测试）
        String token = null;
        String headerAuth = request.getHeaders().getFirst("Authorization");

        if (headerAuth != null && headerAuth.startsWith("Bearer ")) {
            token = headerAuth.substring(7);
        } else {
            // Header 没有，尝试从 URL 取
            token = request.getQueryParams().getFirst("token");
        }

        // 3. 校验 Token
        String userId = null;
        if (token != null) {
            userId = JwtUtil.getUserId(token);
        }

        // 4. 校验失败 -> 401
        if (userId == null) {
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
            String message = "{\"code\": 401, \"message\": \"非法访问，Token无效或缺失\"}";
            DataBuffer buffer = response.bufferFactory().wrap(message.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        }

        // 5. 【核心修改】构建新请求头，传递给下游
        ServerHttpRequest.Builder requestBuilder = request.mutate();

        // 5.1 必定传递 UserID
        requestBuilder.header("X-User-Id", userId);

        // 5.2 【关键修复】如果原始请求 Header 里没 Token（说明是从 URL 拿的）
        // 我们必须手动把它加进 Header，否则 Consumer 的拦截器拿不到 Token！
        if (headerAuth == null) {
            // 补全标准 Bearer 格式
            requestBuilder.header("Authorization", "Bearer " + token);
        }

        ServerHttpRequest newRequest = requestBuilder.build();

        // 放行
        System.out.println("Token 校验通过");
        return chain.filter(exchange.mutate().request(newRequest).build());
    }

    @Override
    public int getOrder() {
        return -1;
    }
}