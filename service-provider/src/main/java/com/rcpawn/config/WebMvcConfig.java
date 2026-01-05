package com.rcpawn.config;

import com.rcpawn.common.intercepter.UserInfoInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册 Common 包里的拦截器
        registry.addInterceptor(new UserInfoInterceptor())
                .addPathPatterns("/**"); // 拦截所有路径
    }
}