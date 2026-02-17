package com.rcpawn.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
@Slf4j
public class LogBuffer {

    @Autowired
    private ReactiveStringRedisTemplate reactiveRedisTemplate;

    // Redis List Key，只存最近 50 条供 Dashboard 展示
    private static final String KEY_INTERCEPT_LOGS = "gateway:dashboard:logs";

    // 预编译时间格式，提升性能
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static final String LOG_ALREADY_HANDLED = "LOG_ALREADY_HANDLED";

    /**
     * 记录日志 (Reactive Fire-and-Forget 模式)
     */
    public void record(String source, String type, String detail) {
        try {
            // 1. 构造 JSON
            String safeDetail = detail == null ? "" : detail.replace("\"", "'");
            String logJson = String.format(
                    "{\"time\":\"%s\", \"source\":\"%s\", \"type\":\"%s\", \"msg\":\"%s\"}",
                    LocalDateTime.now().format(TIME_FORMATTER),
                    source,
                    type,
                    safeDetail
            );

            // 2. 异步推入 Redis List (非阻塞)
            reactiveRedisTemplate.opsForList().leftPush(KEY_INTERCEPT_LOGS, logJson)
                    .flatMap(count -> {
                        // 3. 如果超过 50 条，修剪 (非阻塞)
                        if (count > 50) {
                            return reactiveRedisTemplate.opsForList().trim(KEY_INTERCEPT_LOGS, 0, 49);
                        }
                        return reactor.core.publisher.Mono.empty();
                    })
                    .subscribe(
                            null,
                            e -> log.error("LogBuffer write failed", e)
                    );

        } catch (Exception e) {
            log.error("LogBuffer logic error", e);
        }
    }
}