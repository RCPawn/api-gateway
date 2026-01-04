package com.rcpawn.config;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerResponse;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class GatewayConfiguration {

    @PostConstruct
    public void doInit() {
        // 自定义限流/熔断后的异常返回
        BlockRequestHandler blockRequestHandler = (serverWebExchange, throwable) -> {
            Map<String, Object> map = new HashMap<>();
            map.put("code", 500);
            map.put("msg", "系统繁忙，也就是熔断降级了！(Sentinel)");

            return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(map));
        };

        GatewayCallbackManager.setBlockHandler(blockRequestHandler);
    }
}