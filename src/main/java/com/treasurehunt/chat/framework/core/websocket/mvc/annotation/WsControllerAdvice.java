package com.treasurehunt.chat.framework.core.websocket.mvc.annotation;

import java.lang.annotation.*;

/**
 * WebSocket控制器通知注解
 * 完全类似Spring MVC的@ControllerAdvice，用于全局异常处理
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WsControllerAdvice {
    
    /**
     * 指定要处理的包路径
     */
    String[] basePackages() default {};
    
    /**
     * 指定要处理的Controller类
     */
    Class<?>[] basePackageClasses() default {};
}
