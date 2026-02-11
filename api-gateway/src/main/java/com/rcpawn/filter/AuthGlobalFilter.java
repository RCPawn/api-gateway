package com.rcpawn.filter;

import com.rcpawn.common.util.JwtUtil;
import com.rcpawn.util.LogBuffer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired private LogBuffer logBuffer;

    // Redis Key 常量定义（整合第二份代码的命名规范）
    private static final String KEY_ROUTE_RANK = "gateway:metrics:routes:rank";
    private static final String KEY_METRICS_PREFIX = "gateway:metrics:";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1. 记录请求开始时间（用于计算耗时）
        long startTime = System.currentTimeMillis();

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // ========== 1. 白名单放行 ==========
        if (path.contains("/auth/login") ||
                path.contains("/doc.html") ||
                path.contains("/v3/api-docs") ||
                path.contains("/webjars") ||
                path.contains("/log/logs") ||
                path.contains("/favicon.ico") ||
                path.contains("/dashboard")) { // 大屏接口白名单
            // 白名单请求直接放行，后置执行埋点
            return chain.filter(exchange)
                    .then(Mono.fromRunnable(() -> recordMetrics(exchange, startTime)));
        }

        // ========== 2. 获取 Token ==========
        String token = null;
        String headerAuth = request.getHeaders().getFirst("Authorization");

        if (headerAuth != null && headerAuth.startsWith("Bearer ")) {
            token = headerAuth.substring(7);
        } else {
            token = request.getQueryParams().getFirst("token");
        }

        // ========== 3. 校验 Token ==========
        String userId = null;
        if (token != null) {
            userId = JwtUtil.getUserId(token);
        }

        // ========== 4. 校验失败 -> 401 ==========
        if (userId == null) {
            // 记录日志！
            String ip = Objects.requireNonNull(request.getRemoteAddress()).getAddress().getHostAddress();
            logBuffer.record(ip, "AUTH", "Token Invalid");
            // 2. 【新增】打上标记：告诉后面的日志过滤器，这个我处理过了
            exchange.getAttributes().put(LogBuffer.LOG_ALREADY_HANDLED, true);

            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
            String message = "{\"code\": 401, \"message\": \"非法访问，Token无效或缺失\"}";
            DataBuffer buffer = response.bufferFactory().wrap(message.getBytes(StandardCharsets.UTF_8));
            // 校验失败也记录埋点（统计401错误）
            return response.writeWith(Mono.just(buffer))
                    .then(Mono.fromRunnable(() -> recordMetrics(exchange, startTime)));
        }

        // ========== 5. 透传 UserID ==========
        ServerHttpRequest.Builder requestBuilder = request.mutate();
        requestBuilder.header("X-User-Id", userId);

        if (headerAuth == null) {
            requestBuilder.header("Authorization", "Bearer " + token);
        }

        ServerHttpRequest newRequest = requestBuilder.build();

        // ========== 6. 核心修改：请求响应后执行埋点 ==========
        return chain.filter(exchange.mutate().request(newRequest).build())
                .then(Mono.fromRunnable(() -> recordMetrics(exchange, startTime)));
    }

    /**
     * 后置埋点逻辑：统计延迟、错误率、QPS、路由排行
     * @param exchange 网关交换对象
     * @param startTime 请求开始时间（毫秒）
     */
    private void recordMetrics(ServerWebExchange exchange, long startTime) {
        try {
            // 1. 计算请求耗时（毫秒）
            long duration = System.currentTimeMillis() - startTime;

            // 2. 获取响应状态码，判断是否为错误请求（4xx/5xx）
            ServerHttpResponse response = exchange.getResponse();
            int statusCode = response.getStatusCode() != null ? response.getStatusCode().value() : 500;
            boolean isError = statusCode >= 400;

            // 3. 时间窗口：按分钟聚合（格式 HHmm，如 1045 代表10点45分）
            String minuteWindow = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmm"));

            // 4. 按分钟统计核心指标（原子递增 + 过期时间）
            // 4.1 分钟级总请求数
            String keyTotalReq = KEY_METRICS_PREFIX + "req_count:" + minuteWindow;
            redisTemplate.opsForValue().increment(keyTotalReq, 1);
            redisTemplate.expire(keyTotalReq, 5, TimeUnit.MINUTES);

            // 4.2 分钟级总耗时（用于计算平均延迟）
            String keyTotalLatency = KEY_METRICS_PREFIX + "latency_sum:" + minuteWindow;
            redisTemplate.opsForValue().increment(keyTotalLatency, duration);
            redisTemplate.expire(keyTotalLatency, 5, TimeUnit.MINUTES);

            // 4.3 分钟级错误请求数（仅4xx/5xx统计）
            if (isError) {
                String keyErrorReq = KEY_METRICS_PREFIX + "error_count:" + minuteWindow;
                redisTemplate.opsForValue().increment(keyErrorReq, 1);
                redisTemplate.expire(keyErrorReq, 5, TimeUnit.MINUTES);
            }

            // 5. 路由访问排行（保留原有逻辑）
            Object routeObj = exchange.getAttribute(org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            if (routeObj != null) {
                org.springframework.cloud.gateway.route.Route route = (org.springframework.cloud.gateway.route.Route) routeObj;
                // 1. 增加分数
                redisTemplate.opsForZSet().incrementScore(KEY_ROUTE_RANK, route.getId(), 1);
                // 2. 修剪（建议：如果不是必须实时极其精准，可以不写在主流程，或者用 Lua）
                redisTemplate.opsForZSet().removeRange(KEY_ROUTE_RANK, 0, -11);
                // 3. 兜底设置一个过期时间（比如 7 天），防止僵尸数据
                redisTemplate.expire(KEY_ROUTE_RANK, 7, TimeUnit.DAYS);
            }

            // 6. 秒级QPS（用于实时波形图，过期时间缩短为1分钟）
            String secondWindow = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            String keyQps = KEY_METRICS_PREFIX + "qps:" + secondWindow;
            redisTemplate.opsForValue().increment(keyQps, 1);
            redisTemplate.expire(keyQps, 1, TimeUnit.MINUTES);

        } catch (Exception e) {
            log.error("Redis metrics record error: ", e);
        }
    }

    @Override
    public int getOrder() {
        return -1;
    }
}