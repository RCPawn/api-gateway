package com.rcpawn.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Slf4j
public class LogBuffer {

    @Autowired
    private ReactiveStringRedisTemplate reactiveRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String KEY_INTERCEPT_LOGS = "gateway:dashboard:logs";
    /** 驾驶舱可直接展示的本地时间（含年月日） */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static final String LOG_ALREADY_HANDLED = "LOG_ALREADY_HANDLED";

    /**
     * 拦截事件结构化记录（推荐）：含客户端、HTTP、规则摘要等，便于驾驶舱定位问题。
     */
    public record InterceptRecord(
            String clientIp,
            String type,
            String msg,
            String method,
            String path,
            Integer status,
            String rule,
            long ts
    ) {
        public static InterceptRecord of(String ip, String type, String msg) {
            return new InterceptRecord(ip, type, msg, null, null, null, null, System.currentTimeMillis());
        }
    }

    /**
     * 兼容旧调用：仅 IP + 类型 + 文案
     */
    public void record(String source, String type, String detail) {
        write(toPayload(InterceptRecord.of(source, type, detail)));
    }

    public void record(InterceptRecord record) {
        write(toPayload(record));
    }

    private Map<String, Object> toPayload(InterceptRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ts", r.ts());
        m.put("time", LocalDateTime.now().format(TIME_FORMATTER));
        m.put("clientIp", r.clientIp());
        // 与旧字段对齐：source 原表示客户端标识（多为 IP）
        m.put("source", r.clientIp());
        m.put("type", r.type());
        if (r.msg() != null) {
            m.put("msg", r.msg());
        }
        if (r.method() != null && !r.method().isEmpty()) {
            m.put("method", r.method());
        }
        if (r.path() != null && !r.path().isEmpty()) {
            m.put("path", r.path());
        }
        if (r.status() != null) {
            m.put("status", r.status());
        }
        if (r.rule() != null && !r.rule().isEmpty()) {
            m.put("rule", r.rule());
        }
        return m;
    }

    private void write(Map<String, Object> payload) {
        try {
            String logJson = objectMapper.writeValueAsString(payload);
            reactiveRedisTemplate.opsForList().leftPush(KEY_INTERCEPT_LOGS, logJson)
                    .flatMap(count -> {
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
            log.error("LogBuffer serialize/push failed", e);
        }
    }
}
