package com.rcpawn.common.intercepter;

import com.rcpawn.common.util.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

public class UserInfoInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1. 获取 UserID (网关解析后传过来的)
        String userId = request.getHeader("X-User-Id");
        if (StringUtils.hasText(userId)) {
            UserContext.setUserId(userId);
        }
        // 2. 【关键新增】获取原始 Token (Authorization: Bearer xxx)
        String token = request.getHeader("Authorization");
        if (StringUtils.hasText(token)) {
            UserContext.setToken(token);
        }

        /*// 1. 尝试从 Header 拿 TraceId
        String traceId = request.getHeader("X-Trace-Id");
        // 2. 如果没有（可能是直接调用的微服务，没走网关），就自己生成一个兜底
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        // 3. 放入 MDC，这样日志里就能打印出来了
        MDC.put("traceId", traceId);*/

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 必须清理，防止线程池复用导致数据混乱
        UserContext.remove();
        // 必须清理！否则线程池复用会导致 TraceId 混乱
        // MDC.remove("traceId");
    }
}