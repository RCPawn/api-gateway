package com.rcpawn.common.intercepter;

import com.rcpawn.common.util.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

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

        System.out.println("Header -> ThreadLocal");
        
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 必须清理，防止线程池复用导致数据混乱
        UserContext.remove();
    }
}