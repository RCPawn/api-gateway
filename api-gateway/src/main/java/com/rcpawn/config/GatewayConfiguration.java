package com.rcpawn.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * 历史占位：Sentinel 网关 BlockHandler 已统一在 {@link SentinelConfig} 中注册（含拦截日志与结构化 JSON）。
 * 不再在此处调用 {@link com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager#setBlockHandler}，
 * 避免与 {@link SentinelConfig} 互相覆盖导致驾驶舱缺少熔断/限流日志或「来源」异常。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@Configuration
public class GatewayConfiguration {

    @PostConstruct
    public void doInit() {
        // BlockHandler 由 SentinelConfig 统一注册
    }
}
