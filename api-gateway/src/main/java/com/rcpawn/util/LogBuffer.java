package com.rcpawn.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
public class LogBuffer {

    @Autowired
    private StringRedisTemplate redisTemplate;

    // Redis List Key，只存最近 50 条供 Dashboard 展示
    private static final String KEY_INTERCEPT_LOGS = "gateway:dashboard:logs";
    // 预编译时间格式，提升性能
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    // 用于标记该请求已被拦截（记录了 Redis），无需再写入 MQ
    public static final String LOG_ALREADY_HANDLED = "LOG_ALREADY_HANDLED";

    /**
     * 记录日志 (Fire and Forget 模式)
     */
    public void record(String source, String type, String detail) {
        // 🌟 关键：使用异步执行，绝不阻塞网关主线程
        CompletableFuture.runAsync(() -> {
            try {
                // 1. 构造 JSON
                // 处理一下 detail 里的双引号，防止 JSON 格式错乱
                String safeDetail = detail == null ? "" : detail.replace("\"", "'");

                String logJson = String.format(
                        "{\"time\":\"%s\", \"source\":\"%s\", \"type\":\"%s\", \"msg\":\"%s\"}",
                        LocalDateTime.now().format(TIME_FORMATTER),
                        source,
                        type,
                        safeDetail
                );

                // 2. 推入 Redis List
                redisTemplate.opsForList().leftPush(KEY_INTERCEPT_LOGS, logJson);

                // 3. 修剪 List (保留最近 50 条够用了，太多了前端也显示不下)
                redisTemplate.opsForList().trim(KEY_INTERCEPT_LOGS, 0, 49);

            } catch (Exception e) {
                // Redis 挂了就挂了，记录个 Error Log 即可，别抛异常
                log.error("Dashboard LogBuffer error: {}", e.getMessage());
            }
        });
    }
}