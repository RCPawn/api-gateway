package com.rcpawn.config;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.rcpawn.util.LogBuffer;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.Objects;

@Configuration
public class SentinelConfig {

    @Autowired
    private LogBuffer logBuffer;

    @PostConstruct
    public void init() {
        GatewayCallbackManager.setBlockHandler((exchange, t) -> {
            // 记录日志！
            String ip = Objects.requireNonNull(exchange.getRequest().getRemoteAddress()).getAddress().getHostAddress();
            String path = exchange.getRequest().getURI().getPath();
            
            // 判断是限流还是熔断
            String type = "FLOW";
            String msg = "QPS Limit: " + path;
            if (t instanceof DegradeException) {
                type = "FUSE";
                msg = "Service Degraded";
            }
            
            logBuffer.record(ip, type, msg); // 👈 新增这行
            // 2. 【新增】打上标记
            exchange.getAttributes().put(LogBuffer.LOG_ALREADY_HANDLED, true);

            // 返回自定义的 JSON 响应
            return ServerResponse.status(429)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue("{\"code\":429, \"msg\":\"" + msg + "\"}"));
        });
    }
}