package com.rcpawn.common.intercepter;

import com.rcpawn.common.util.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

public class UserInfoInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1. 从 Header 中获取网关透传的 User-Id
        String userId = request.getHeader("X-User-Id");
        
        // 2. 如果不为空，存入 ThreadLocal
        if (StringUtils.hasText(userId)) {
            UserContext.setUserId(userId);
        }
        
        // 3. 放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 4. 请求结束，必须清理 ThreadLocal，防止内存泄漏
        UserContext.remove();
    }
}