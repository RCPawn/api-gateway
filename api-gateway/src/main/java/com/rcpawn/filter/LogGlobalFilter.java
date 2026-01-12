package com.rcpawn.filter;

import com.rcpawn.common.entity.GatewayLogDTO; // 注意引入的是 common 里的类
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Date;

@Component
public class LogGlobalFilter implements GlobalFilter, Ordered {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1. 记录开始时间
        long startTime = System.currentTimeMillis();

        // 2. 链式调用，等请求回来后再记录
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            long endTime = System.currentTimeMillis();
            
            // 3. 封装日志对象
            GatewayLogDTO log = new GatewayLogDTO();
            log.setPath(exchange.getRequest().getURI().getPath());
            log.setMethod(exchange.getRequest().getMethod().name());
            log.setIp(exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
            log.setUserId(exchange.getRequest().getHeaders().getFirst("X-User-Id"));
            log.setStatus(exchange.getResponse().getStatusCode().value());
            log.setResponseTime(endTime - startTime);
            log.setRequestTime(new Date(startTime));
            // TraceID 暂时留空，或者从 SkyWalking 上下文取(如果有)
            
            // 4. 发送 MQ (异步)
            try {
                rabbitTemplate.convertAndSend("gateway_log_queue", log);
                System.out.println("✅ [网关] 异步日志已发送 MQ: " + log.getPath());
            } catch (Exception e) {
                System.err.println("❌ [网关] 日志发送失败: " + e.getMessage());
            }
        }));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE; // 最低优先级，最后执行
    }
}