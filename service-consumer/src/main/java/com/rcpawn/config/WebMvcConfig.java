package com.rcpawn.config;

import com.rcpawn.common.intercepter.UserInfoInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册我们写的拦截器，拦截所有路径
        registry.addInterceptor(new UserInfoInterceptor())
                .addPathPatterns("/**");
    }
}