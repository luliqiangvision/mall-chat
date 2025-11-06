package com.treasurehunt.chat.framework.core.websocket.mvc.annotation;

import java.lang.annotation.*;

/**
 * WebSocket异常处理器注解
 * 完全类似Spring MVC的@ExceptionHandler
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WsExceptionHandler {
    
    /**
     * 要处理的异常类型数组
     */
    Class<? extends Throwable>[] value() default {};
}
