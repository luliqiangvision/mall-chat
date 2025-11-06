package com.treasurehunt.chat.framework.core.websocket.mvc.config;

import java.lang.annotation.*;

/**
 * 启用WebSocket MVC配置注解
 * 完全类似Spring MVC的@EnableWebMvc
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EnableWebSocketMvc {
    
    /**
     * Controller 扫描包路径（别名）
     */
    String[] value() default {};

    /**
     * Controller 扫描包路径
     */
    String[] basePackages() default {};

    /**
     * 以类型推导基础包
     */
    Class<?>[] basePackageClasses() default {};
}
