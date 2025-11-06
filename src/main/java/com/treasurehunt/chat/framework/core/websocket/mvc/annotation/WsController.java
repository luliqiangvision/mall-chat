package com.treasurehunt.chat.framework.core.websocket.mvc.annotation;

import java.lang.annotation.*;

/**
 * WebSocket 控制器注解
 * 用于标记 WS MVC 控制器类，便于精确扫描
 * 支持设置路径前缀，类似Spring MVC的@RequestMapping
 * 
 * 使用示例：
 * @WsController("/agent")
 * public class AgentServiceChatController {
 *     @WsRequestMapping("/sendMessage")
 *     public ResponsePayload sendMessage(...) { ... }
 * }
 * 最终路径为: /agent/sendMessage
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WsController {
    
    /**
     * 控制器路径前缀
     * 所有方法上的@WsRequestMapping路径都会加上这个前缀
     * @return 路径前缀，默认为空字符串
     */
    String value() default "";
}


