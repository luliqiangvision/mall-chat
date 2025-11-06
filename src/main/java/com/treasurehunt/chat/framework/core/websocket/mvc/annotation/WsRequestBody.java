package com.treasurehunt.chat.framework.core.websocket.mvc.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * WebSocket请求体注解
 * 用于标记需要自动转换的payload参数，完全类似Spring MVC的@RequestBody
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface WsRequestBody {
    /**
     * 可选：指定payload类型，如果不指定则从方法参数推断
     */
    Class<?> value() default Void.class;
}