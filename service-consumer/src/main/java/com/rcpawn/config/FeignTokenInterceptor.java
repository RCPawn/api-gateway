package com.rcpawn.config;

import com.rcpawn.common.util.UserContext;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class FeignTokenInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        // 打印日志看看拦截器进来了没（调试用）
//        System.out.println(">>> Feign拦截器触发，准备中继 Token...");

        // 从当前线程的 ThreadLocal 里拿到 Token
        String token = UserContext.getToken();
        String userId = UserContext.getUserId();

        // 打印看看拿到没
//        System.out.println(">>> 中继数据 - Token: " + (token != null) + ", UserID: " + userId);

        // 如果有 Token，塞入 Feign 请求的 Header
        if (StringUtils.hasText(token)) {
            template.header("Authorization", token);
        }
//        System.out.println("ThreadLocal -> Header");
        // 把 UserID 也传下去 (方便下游直接用，不用再解析 Token)
        if (StringUtils.hasText(userId)) {
            template.header("X-User-Id", userId);
        }
    }
}