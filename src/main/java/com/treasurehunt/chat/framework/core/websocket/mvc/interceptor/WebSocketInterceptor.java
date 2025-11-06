package com.treasurehunt.chat.framework.core.websocket.mvc.interceptor;

import com.treasurehunt.chat.framework.core.websocket.mvc.model.WebSocketDataWrapper;
import org.springframework.web.socket.WebSocketSession;

/**
 * WebSocket拦截器接口
 * 完全类似Spring MVC的HandlerInterceptor
 */
public interface WebSocketInterceptor {
    
    /**
     * 在消息处理前执行
     * @param session WebSocket会话
     * @param message 原始消息
     * @return true继续处理，false中断
     */
    default boolean preHandle(WebSocketSession session, WebSocketDataWrapper message) {
        return true;
    }
    
    /**
     * 在消息处理后执行（成功时）
     * @param session WebSocket会话
     * @param message 原始消息
     */
    default void postHandle(WebSocketSession session, WebSocketDataWrapper message) {
    }
    
    /**
     * 无论成功失败都会执行
     * @param session WebSocket会话
     * @param message 原始消息
     * @param ex 异常（如果有）
     */
    default void afterCompletion(WebSocketSession session, WebSocketDataWrapper message, Exception ex) {
    }
}
