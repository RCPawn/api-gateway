package com.rcpawn.config;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.Rule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowException;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rcpawn.util.LogBuffer;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Configuration
@Order(Ordered.LOWEST_PRECEDENCE)
public class SentinelConfig {

    @Autowired
    private LogBuffer logBuffer;

    @Autowired
    private ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        GatewayCallbackManager.setBlockHandler((exchange, t) -> {
            String ip = Objects.requireNonNull(exchange.getRequest().getRemoteAddress()).getAddress().getHostAddress();
            String path = exchange.getRequest().getURI().getPath();
            String method = exchange.getRequest().getMethod().name();

            String type = "FLOW";
            if (t instanceof DegradeException) {
                type = "FUSE";
            } else if (t instanceof ParamFlowException) {
                type = "PARAM_FLOW";
            }

            path = effectiveDisplayPath(path, t);
            String ruleSummary = summarizeSentinelRule(t);
            String msg = buildBlockMessage(type, method, path, t);

            logBuffer.record(new LogBuffer.InterceptRecord(
                    ip,
                    type,
                    msg,
                    method,
                    path,
                    429,
                    ruleSummary,
                    System.currentTimeMillis()
            ));
            exchange.getAttributes().put(LogBuffer.LOG_ALREADY_HANDLED, true);

            String bodyJson;
            try {
                Map<String, Object> body = new HashMap<>();
                body.put("code", 429);
                body.put("msg", msg);
                body.put("type", type);
                if (!ruleSummary.isEmpty()) {
                    body.put("rule", ruleSummary);
                }
                bodyJson = objectMapper.writeValueAsString(body);
            } catch (Exception e) {
                bodyJson = "{\"code\":429,\"msg\":\"请求过于频繁，请稍后重试\"}";
            }

            return ServerResponse.status(429)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(bodyJson));
        });
    }

    /**
     * 驾驶舱「来源」依赖 path/msg；熔断等场景下 URI 可能为空或仅为 "/"，此时用语义化的 Sentinel resource 补全。
     */
    private static String effectiveDisplayPath(String uriPath, Throwable t) {
        String p = uriPath == null ? "" : uriPath.trim();
        if (!p.isEmpty() && !"/".equals(p)) {
            return p;
        }
        if (t instanceof BlockException be) {
            Rule rule = be.getRule();
            if (rule != null) {
                String res = rule.getResource();
                if (res != null && !res.isBlank()) {
                    if (res.startsWith("Route:")) {
                        res = res.substring("Route:".length());
                    }
                    return res.startsWith("/") ? res : "/" + res;
                }
            }
        }
        return p;
    }

    private static String buildBlockMessage(String type, String method, String path, Throwable t) {
        String reason = t != null ? t.getClass().getSimpleName() : "Block";
        return switch (type) {
            case "FUSE" -> String.format("熔断降级 | %s %s | %s", method, path, reason);
            case "PARAM_FLOW" -> String.format("热点参数限流 | %s %s | %s", method, path, reason);
            default -> String.format("限流 | %s %s | %s", method, path, reason);
        };
    }

    private static String summarizeSentinelRule(Throwable t) {
        if (!(t instanceof BlockException)) {
            return "";
        }
        Rule rule = ((BlockException) t).getRule();
        if (rule == null) {
            return "";
        }
        String resource = rule.getResource() != null ? rule.getResource() : "";
        if (t instanceof FlowException && rule instanceof FlowRule fr) {
            return String.format("resource=%s 阈值QPS=%s grade=%s", resource, fr.getCount(), fr.getGrade());
        }
        if (t instanceof DegradeException && rule instanceof DegradeRule dr) {
            return String.format("resource=%s grade=%s threshold=%s", resource, dr.getGrade(), dr.getCount());
        }
        if (t instanceof ParamFlowException && rule instanceof ParamFlowRule pr) {
            return String.format("resource=%s 热点阈值=%s 参数索引=%s", resource, pr.getCount(), pr.getParamIdx());
        }
        return "resource=" + resource;
    }
}
