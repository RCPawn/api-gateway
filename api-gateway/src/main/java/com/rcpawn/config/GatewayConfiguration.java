package com.rcpawn.config;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowException;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class GatewayConfiguration {

    @PostConstruct
    public void doInit() {
        BlockRequestHandler blockRequestHandler = (serverWebExchange, throwable) -> {
            Map<String, Object> map = new HashMap<>();

            // 默认状态码 429
            int status = HttpStatus.TOO_MANY_REQUESTS.value();
            String msg = "系统繁忙，请稍后再试";

            // 不管是 Flow 还是 ParamFlow，统一告诉用户“限流了”
            if (throwable instanceof BlockException) { // BlockException 是所有限流熔断的父类
                msg = "访问过于频繁，请稍后再试";
                // 如果是熔断，依然可以单独判断 DegradeException
                if (throwable instanceof DegradeException) {
                    msg = "服务熔断，暂时不可用";
                }
            }

            map.put("code", status);
            map.put("msg", msg);

            return ServerResponse.status(status)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(map));
        };

        GatewayCallbackManager.setBlockHandler(blockRequestHandler);
    }
}