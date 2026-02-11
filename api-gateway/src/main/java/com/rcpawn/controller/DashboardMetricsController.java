package com.rcpawn.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rcpawn.common.util.Result;
import com.rcpawn.service.SkyWalkingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/dashboard/metrics")
public class DashboardMetricsController {

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private SkyWalkingService skyWalkingService;

    private static final String KEY_METRICS_PREFIX = "gateway:metrics:";
    private static final String KEY_ROUTE_RANK = "gateway:metrics:routes:rank";

    @GetMapping("/topology")
    public Result<Map<String, Object>> getTopology() {
        return Result.success(skyWalkingService.getTopology());
    }

    @GetMapping("/realtime")
    public Result<Map<String, Object>> getRealtimeMetrics() {
        Map<String, Object> data = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();

        try {
            // ===== 1. 获取实时 QPS (最近 5 秒平均) =====
            // 这里用秒级 Key
            int qps = 0;
            for (int i = 0; i < 5; i++) {
                String second = now.minusSeconds(i).format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                String val = redisTemplate.opsForValue().get(KEY_METRICS_PREFIX + "qps:" + second);
                if (val != null) qps += Integer.parseInt(val);
            }
            qps = qps / 5; // 取平均，避免波动太大

            // ===== 2. 获取延迟和错误率 (取当前分钟的数据) =====
            // 这里用分钟级 Key (HHmm)
            String timeWindow = now.format(DateTimeFormatter.ofPattern("HHmm"));

            // 获取总请求数
            String reqCountStr = redisTemplate.opsForValue().get(KEY_METRICS_PREFIX + "req_count:" + timeWindow);
            long totalReq = reqCountStr == null ? 0 : Long.parseLong(reqCountStr);

            long avgLatency = 0;
            double errorRateVal = 0.0;

            if (totalReq > 0) {
                // 计算平均延迟
                String latencySumStr = redisTemplate.opsForValue().get(KEY_METRICS_PREFIX + "latency_sum:" + timeWindow);
                long totalLatency = latencySumStr == null ? 0 : Long.parseLong(latencySumStr);
                avgLatency = totalLatency / totalReq;

                // 计算错误率
                String errorCountStr = redisTemplate.opsForValue().get(KEY_METRICS_PREFIX + "error_count:" + timeWindow);
                long totalError = errorCountStr == null ? 0 : Long.parseLong(errorCountStr);
                errorRateVal = (double) totalError / totalReq * 100;
            }

            // 格式化错误率 (保留2位小数)
            DecimalFormat df = new DecimalFormat("0.00");
            String errorRateStr = df.format(errorRateVal) + "%";

            // ===== 3. 获取 Top 路由 =====
            Set<ZSetOperations.TypedTuple<String>> topRoutes = redisTemplate.opsForZSet()
                    .reverseRangeWithScores(KEY_ROUTE_RANK, 0, 9);
            List<Map<String, Object>> routeList = new ArrayList<>();
            if (topRoutes != null && !topRoutes.isEmpty()) {
                double maxScore = topRoutes.iterator().next().getScore();
                if (maxScore == 0) maxScore = 1;
                for (ZSetOperations.TypedTuple<String> tuple : topRoutes) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("name", tuple.getValue());
                    map.put("count", tuple.getScore().intValue());
                    map.put("percent", (int)((tuple.getScore() / maxScore) * 100));
                    routeList.add(map);
                }
            }

            // 组装数据
            data.put("qps", qps);
            data.put("latency", avgLatency); // 返回 long 类型
            data.put("errorRate", errorRateStr); // 返回 String 类型 "0.00%"
            data.put("topRoutes", routeList);

        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("获取监控数据失败");
        }

        return Result.success(data);
    }

    @GetMapping("/logs")
    public Result<List<Map<String, String>>> getRecentLogs() {
        // 从 Redis 取出最近 20 条
        List<String> logs = redisTemplate.opsForList().range("gateway:dashboard:logs", 0, 19);

        List<Map<String, String>> result = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();

        if (logs != null) {
            for (String json : logs) {
                try {
                    // 转成 Map 返回给前端
                    result.add(mapper.readValue(json, Map.class));
                } catch (Exception e) {}
            }
        }
        return Result.success(result);
    }
}