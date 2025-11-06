package com.treasurehunt.chat.framework.core.websocket.mvc.interceptor;

import com.treasurehunt.chat.framework.core.websocket.mvc.model.WebSocketDataWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * WebSocket拦截器注册器
 * 类似Spring MVC的WebMvcConfigurer.addInterceptors
 */
@Slf4j
@Component
public class WebSocketInterceptorRegistry {
    
    private final List<WebSocketInterceptor> interceptors = new ArrayList<>();
    
    @Autowired(required = false)
    private List<WebSocketInterceptor> webSocketInterceptors;
    
    @PostConstruct
    public void initInterceptors() {
        if (webSocketInterceptors != null) {
            interceptors.addAll(webSocketInterceptors);
            log.info("注册了 {} 个WebSocket拦截器", interceptors.size());
        }
    }
    
    /**
     * 执行preHandle阶段
     */
    public boolean preHandle(WebSocketSession session, WebSocketDataWrapper message) {
        for (WebSocketInterceptor interceptor : interceptors) {
            if (!interceptor.preHandle(session, message)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * 执行postHandle阶段
     */
    public void postHandle(WebSocketSession session, WebSocketDataWrapper message) {
        for (WebSocketInterceptor interceptor : interceptors) {
            try {
                interceptor.postHandle(session, message);
            } catch (Exception e) {
                log.warn("拦截器postHandle执行失败", e);
            }
        }
    }
    
    /**
     * 执行afterCompletion阶段
     */
    public void afterCompletion(WebSocketSession session, WebSocketDataWrapper message, Exception ex) {
        for (WebSocketInterceptor interceptor : interceptors) {
            try {
                interceptor.afterCompletion(session, message, ex);
            } catch (Exception e) {
                log.warn("拦截器afterCompletion执行失败", e);
            }
        }
    }
}
