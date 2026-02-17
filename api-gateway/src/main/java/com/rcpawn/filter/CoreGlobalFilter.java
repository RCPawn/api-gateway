package com.rcpawn.filter;

import com.rcpawn.common.util.JwtUtil;
import com.rcpawn.util.LogBuffer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 修复版：回归高性能 Reactive Pipeline 模式
 */
@Slf4j
@Component
public class CoreGlobalFilter implements GlobalFilter, Ordered {

    @Autowired
    private ReactiveStringRedisTemplate reactiveRedisTemplate;

    @Autowired
    private LogBuffer logBuffer;

    private static final DateTimeFormatter MINUTE_FORMATTER = DateTimeFormatter.ofPattern("HHmm");
    private static final DateTimeFormatter SECOND_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final String KEY_ROUTE_RANK = "gateway:metrics:routes:rank";
    private static final String KEY_METRICS_PREFIX = "gateway:metrics:";
    private static final String SW_HEADER = "sw8";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        exchange.getAttributes().put("startTime", startTime);

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String sw8 = request.getHeaders().getFirst(SW_HEADER);

        // 白名单放行
        if (isWhiteList(path)) {
            return chain.filter(exchange)
                    .then(Mono.defer(() -> recordMetrics(exchange, startTime)));
        }

        // JWT 鉴权
        String token = getToken(request);
        String userId = (token != null) ? JwtUtil.getUserId(token) : null;

        if (userId == null) {
            return handleAuthFail(exchange);
        }

        // 构建请求
        ServerHttpRequest.Builder requestBuilder = request.mutate();
        requestBuilder.header("X-User-Id", userId);

        if (request.getHeaders().getFirst("Authorization") == null && token != null) {
            requestBuilder.header("Authorization", "Bearer " + token);
        }

        if (sw8 != null) {
            requestBuilder.header(SW_HEADER, sw8);
        }

        return chain.filter(exchange.mutate().request(requestBuilder.build()).build())
                .then(Mono.defer(() -> recordMetrics(exchange, startTime)));
    }

    /**
     * 使用 Reactive Pipeline 批量写入，避免 Lua 阻塞
     */
    private Mono<Void> recordMetrics(ServerWebExchange exchange, long startTime) {
        try {
            long duration = System.currentTimeMillis() - startTime;
            ServerHttpResponse response = exchange.getResponse();
            int statusCode = response.getStatusCode() != null ? response.getStatusCode().value() : 500;
            boolean isError = statusCode >= 400;

            LocalDateTime now = LocalDateTime.now();
            String minuteWindow = now.format(MINUTE_FORMATTER);
            String secondWindow = now.format(SECOND_FORMATTER);

            String keyReq = KEY_METRICS_PREFIX + "req_count:" + minuteWindow;
            String keyLat = KEY_METRICS_PREFIX + "latency_sum:" + minuteWindow;
            String keyErr = KEY_METRICS_PREFIX + "error_count:" + minuteWindow;
            String keyQps = KEY_METRICS_PREFIX + "qps:" + secondWindow;

            Object routeObj = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            String routeId = (routeObj != null) ? ((org.springframework.cloud.gateway.route.Route) routeObj).getId() : null;

            // 构建命令流 (Pipeline)
            // 使用 Flux.merge 或 concat 将多个命令合并，Lettuce 会自动优化网络传输
            return Flux.merge(
                            // 1. 总请求 (带过期)
                            reactiveRedisTemplate.opsForValue().increment(keyReq).flatMap(c -> c == 1 ? reactiveRedisTemplate.expire(keyReq, Duration.ofMinutes(5)) : Mono.empty()),

                            // 2. 耗时
                            reactiveRedisTemplate.opsForValue().increment(keyLat, duration).flatMap(c -> c == duration ? reactiveRedisTemplate.expire(keyLat, Duration.ofMinutes(5)) : Mono.empty()),

                            // 3. QPS
                            reactiveRedisTemplate.opsForValue().increment(keyQps).flatMap(c -> c == 1 ? reactiveRedisTemplate.expire(keyQps, Duration.ofMinutes(1)) : Mono.empty()),

                            // 4. 错误 (条件执行)
                            isError ? reactiveRedisTemplate.opsForValue().increment(keyErr).flatMap(c -> c == 1 ? reactiveRedisTemplate.expire(keyErr, Duration.ofMinutes(5)) : Mono.empty()) : Mono.empty(),

                            // 5. 路由排行
                            routeId != null ? reactiveRedisTemplate.opsForZSet().incrementScore(KEY_ROUTE_RANK, routeId, 1) : Mono.empty()
                    )
                    .then() // 等待所有操作完成
                    .subscribeOn(Schedulers.boundedElastic()) // 依然保持异步调度，防止极少数情况下的阻塞
                    .onErrorResume(e -> {
                        log.error("Metrics write error", e);
                        return Mono.empty();
                    });

        } catch (Exception e) {
            log.error("Metrics logic error", e);
            return Mono.empty();
        }
    }

    private Mono<Void> handleAuthFail(ServerWebExchange exchange) {
        if (ThreadLocalRandom.current().nextInt(10) == 0) {
            String ip = Objects.requireNonNull(exchange.getRequest().getRemoteAddress()).getAddress().getHostAddress();
            logBuffer.record(ip, "AUTH", "Token Invalid");
        }

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        String message = "{\"code\": 401, \"message\": \"非法访问\"}";
        DataBuffer buffer = response.bufferFactory().wrap(message.getBytes(StandardCharsets.UTF_8));

        long startTime = (long) exchange.getAttributes().get("startTime");
        return response.writeWith(Mono.just(buffer))
                .then(Mono.defer(() -> recordMetrics(exchange, startTime)));
    }

    private String getToken(ServerHttpRequest request) {
        String headerAuth = request.getHeaders().getFirst("Authorization");
        if (headerAuth != null && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }
        return request.getQueryParams().getFirst("token");
    }

    private boolean isWhiteList(String path) {
        return path.contains("/auth/login") ||
                path.contains("/doc.html") ||
                path.contains("/v3/api-docs") ||
                path.contains("/webjars") ||
                path.contains("/dashboard") ||
                path.contains("/log/logs") ||
                path.contains("/favicon.ico");
    }

    @Override
    public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }
}