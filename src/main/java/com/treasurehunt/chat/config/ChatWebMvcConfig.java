package com.treasurehunt.chat.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 买家/客服 HTTP 注册 {@link ChatRequestContextInterceptor}（登出、info 等不依赖业务线头的接口排除）。
 */
@Configuration
public class ChatWebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private ChatRequestContextInterceptor chatRequestContextInterceptor;

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(chatRequestContextInterceptor)
                .addPathPatterns(
                        "/chat/customer-service/**",
                        "/chat/agent-service/**",
                        "/agent/login",
                        "/agent/identities")
                .order(0);
    }
}
