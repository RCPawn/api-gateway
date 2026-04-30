package com.rcpawn.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;

import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class LogBuffer {

    @Autowired
    private ReactiveStringRedisTemplate reactiveRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String KEY_INTERCEPT_LOGS = "gateway:dashboard:logs";
    /** 与驾驶舱前端一致的累计次数（列表有容量上限，挤出旧项时仍保留总触发次数） */
    public static final String KEY_INTERCEPT_TOTALS = "gateway:dashboard:intercept_totals";
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
        String pathSeg = segmentOrDash(normalizeSourceSegment(
                r.path(),
                r.rule() != null ? r.rule() : "",
                r.msg() != null ? r.msg() : ""));
        m.put("coarseKey", buildCoarseKey(r.type(), pathSeg, r.clientIp(), r.status()));
        return m;
    }

    /**
     * 与 gateway-dashboard {@code normalizeSourcePath} 对齐，用于 coarseKey 与来源补全。
     */
    private static String normalizeSourceSegment(String path, String rule, String msg) {
        String p = path == null ? "" : path.trim();
        String pathStripped = p.replaceFirst("^/+", "");
        if (!p.isEmpty() && !pathStripped.isEmpty()) {
            return pathStripped;
        }
        String m = msg == null ? "" : msg;
        Matcher colon = Pattern.compile(":\\s*(/[\\w\\-./]+)").matcher(m);
        if (colon.find()) {
            return colon.group(1).replaceFirst("^/+", "");
        }
        Matcher http = Pattern.compile("\\b(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\\s+(/\\S+)", Pattern.CASE_INSENSITIVE).matcher(m);
        if (http.find()) {
            return http.group(2).replaceFirst("^/+", "");
        }
        String ru = rule == null ? "" : rule;
        Matcher resInRule = Pattern.compile("resource=([^\\s,]+)").matcher(ru);
        if (resInRule.find()) {
            return stripResourceValue(resInRule.group(1));
        }
        Matcher resInMsg = Pattern.compile("resource=([^\\s,]+)").matcher(m);
        if (resInMsg.find()) {
            return stripResourceValue(resInMsg.group(1));
        }
        return "";
    }

    private static String stripResourceValue(String raw) {
        String res = raw.trim();
        if (res.regionMatches(true, 0, "route:", 0, 6)) {
            res = res.substring(6);
        }
        return res.replaceFirst("^/+", "");
    }

    private static String segmentOrDash(String normalized) {
        return normalized.isEmpty() ? "—" : normalized;
    }

    private static String buildCoarseKey(String type, String pathSegmentOrDash, String clientIp, Integer status) {
        String t = type == null ? "" : type.toUpperCase();
        String seg = pathSegmentOrDash == null || pathSegmentOrDash.isEmpty() ? "—" : pathSegmentOrDash;
        String ip = clientIp == null ? "" : clientIp;
        String st = status == null ? "0" : String.valueOf(status);
        return String.join("\u0001", t, seg, ip, st);
    }

    private void write(Map<String, Object> payload) {
        try {
            String logJson = objectMapper.writeValueAsString(payload);
            Object ck = payload.get("coarseKey");
            String coarseKey = ck != null ? String.valueOf(ck) : "";
            reactiveRedisTemplate.opsForList().leftPush(KEY_INTERCEPT_LOGS, logJson)
                    .flatMap(count -> {
                        Mono<?> trimMono = (count != null && count > 50)
                                ? reactiveRedisTemplate.opsForList().trim(KEY_INTERCEPT_LOGS, 0, 49)
                                : Mono.empty();
                        Mono<Long> incMono = StringUtils.hasText(coarseKey)
                                ? reactiveRedisTemplate.opsForHash().increment(KEY_INTERCEPT_TOTALS, coarseKey, 1)
                                : Mono.just(0L);
                        return trimMono.then(incMono);
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
