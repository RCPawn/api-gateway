package com.rcpawn.controller;

import com.rcpawn.common.util.Result;
import com.rcpawn.filter.WafFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/dashboard/ops") // 注意路径变化
public class DashboardOpsController { // 建议拆分一个新 Controller，或者写在原来的里面

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired private ApplicationEventPublisher publisher; // 用于发布事件

    // 1. REFRESH: 刷新路由
    @PostMapping("/refresh-routes")
    public Result<String> refreshRoutes() {
        // 发送标准的 Spring Cloud Gateway 刷新事件
        publisher.publishEvent(new RefreshRoutesEvent(this));
        return Result.success("路由刷新指令已下发");
    }

    // 2. WAF: 切换防火墙开关
    @PostMapping("/waf")
    public Result<String> toggleWaf(@RequestParam boolean enable) {
        if (enable) {
            redisTemplate.opsForValue().set(WafFilter.WAF_ENABLE_KEY, "true");
            return Result.success("WAF 防火墙已激活！高危 IP 将被拦截。");
        } else {
            redisTemplate.delete(WafFilter.WAF_ENABLE_KEY);
            return Result.success("WAF 防火墙已解除，系统恢复正常访问。");
        }
    }

    // 3. SAMPLE: 开启 60秒 日志采样
    @PostMapping("/sample")
    public Result<String> startSampling() {
        // 设置一个标志位，60秒自动过期
        redisTemplate.opsForValue().set("gateway:log:sample", "true", 60, TimeUnit.SECONDS);
        return Result.success("全量日志采样已开启 (持续60秒)");
    }

    // 4. CLEAN: 清理监控数据
    @PostMapping("/clean")
    public Result<String> cleanMetrics() {
        // 删除所有监控相关的 Key
        Set<String> keys = redisTemplate.keys("gateway:metrics:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        // 同时清理拦截日志缓存
        redisTemplate.delete("gateway:dashboard:logs");
        
        return Result.success("监控数据已重置");
    }
}