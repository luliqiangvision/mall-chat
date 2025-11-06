package com.treasurehunt.chat.framework.core.websocket.mvc.annotation;

import java.lang.annotation.*;

/**
 * WebSocket 请求映射注解
 * 用于映射 WebSocket 消息处理方法，完全类似 Spring MVC的@RequestMapping
 * 
 * 使用示例：
 * @WsRequestMapping("sendMessage")
 * public ResponsePayload sendMessage(@WsRequestBody RequestPayload payload) {
 *     return service.handle(payload);
 * }
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WsRequestMapping {
    
    /**
     * 路由映射值，对应 WebSocket 请求中的 interface 字段
     * @return 路由映射值
     */
    String value();
}
