package com.treasurehunt.chat.framework.core.websocket.mvc.dispatcher;


import com.treasurehunt.chat.framework.core.websocket.mvc.adapt.WebSocketHandlerAdapter;
import com.treasurehunt.chat.framework.core.websocket.mvc.handler.HandlerMethod;
import com.treasurehunt.chat.framework.core.websocket.mvc.handler.WebSocketExceptionHandlerResolver;
import com.treasurehunt.chat.framework.core.websocket.mvc.interceptor.WebSocketInterceptorRegistry;
import com.treasurehunt.chat.framework.core.websocket.mvc.mapping.WebSocketHandlerMapping;
import com.treasurehunt.chat.framework.core.websocket.mvc.model.WebSocketDataWrapper;
import com.treasurehunt.chat.framework.core.websocket.mvc.model.WsResponseEntity;
import com.treasurehunt.chat.framework.core.websocket.distributed.session.LocalWsSession;
import com.treasurehunt.chat.framework.core.websocket.distributed.session.WsSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;


/**
 * WebSocket调度器
 * 核心调度器，类似DispatcherServlet，负责协调各个组件处理WebSocket请求
 */
@Slf4j
@Component  
public class WebSocketDispatcher {
    
    @Autowired
    @Qualifier("customWebSocketHandlerMapping")
    private WebSocketHandlerMapping handlerMapping;
    
    @Autowired  
    private WebSocketHandlerAdapter handlerAdapter;
    
    @Autowired(required = false)
    private WebSocketInterceptorRegistry interceptorRegistry;
    
    @Autowired(required = false)
    private WebSocketExceptionHandlerResolver exceptionHandlerResolver;
    

    
    // 移除硬编码，改用注解配置
    
    /**
     * 调度WebSocket请求
     * 类似DispatcherServlet的doDispatch方法
     * 
     * @param webSocketDataWrapper WebSocket请求
     * @param session WebSocket会话
     */
    public void dispatch(WebSocketDataWrapper<?> webSocketDataWrapper, WebSocketSession session) {
        if (webSocketDataWrapper == null) {
            log.warn("收到空的 WebSocket 请求");
            return;
        }
        
        Object result = null;
        Exception exception = null;
        
        try {
            // 拦截器: preHandle
            if (interceptorRegistry != null && !interceptorRegistry.preHandle(session, webSocketDataWrapper)) {
                log.debug("拦截器阻止了请求处理");
                return;
            }
            
            // 1. 获取接口名称
            String interfaceName = extractInterfaceName(webSocketDataWrapper);
            if (interfaceName == null) {
                log.warn("无法获取请求接口名称: {}", webSocketDataWrapper);
                return;
            }
            
            // 2. 查找处理器方法（Handler Mapping阶段）
            HandlerMethod handlerMethod = handlerMapping.getHandler(interfaceName);
            if (handlerMethod != null) {
                // 3. 调用处理器适配器（Handler Adapter阶段）
                result = handlerAdapter.handle(handlerMethod, webSocketDataWrapper, session);
                
                // 拦截器: postHandle
                if (interceptorRegistry != null) {
                    interceptorRegistry.postHandle(session, webSocketDataWrapper);
                }
                
                // 4. 处理返回值 - 自动封装并发送响应
                if (result != null) {
                    sendAutomaticResponse(handlerMethod, result, session);
                }
                
                log.debug("成功处理 WebSocket 请求: {}", interfaceName);
            } else {
                log.warn("未找到对应的 WebSocket 处理器: {}", interfaceName);
                sendNotFoundResponse(interfaceName, session);
            }
            
        } catch (Exception e) {
            exception = e;
            log.error("处理 WebSocket 请求时发生异常，接口: {}, 错误: {}", webSocketDataWrapper.getInterfaceName(), e.getMessage(), e);
            result = handleException(e, session);
            
            if (result != null) {
                sendErrorResponse(webSocketDataWrapper, result.toString(), session);
            }
        } finally {
            // 拦截器: afterCompletion
            if (interceptorRegistry != null) {
                interceptorRegistry.afterCompletion(session, webSocketDataWrapper, exception);
            }
        }
    }
    
    /**
     * 处理异常
     */
    private Object handleException(Exception e, WebSocketSession session) {
        if (exceptionHandlerResolver != null) {
            WebSocketExceptionHandlerResolver.ExceptionHandlerInfo handlerInfo = 
                exceptionHandlerResolver.resolveExceptionHandler(e);
            if (handlerInfo != null) {
                try {
                    return handlerInfo.getMethod().invoke(handlerInfo.getBean(), e, session);
                } catch (Exception ex) {
                    log.error("异常处理器执行失败", ex);
                }
            }
        }
        return "处理失败: " + e.getMessage();
    }
    
    /**
     * 从请求中提取接口名称
     */
    private String extractInterfaceName(WebSocketDataWrapper<?> request) throws Exception {
        return request.getInterfaceName();
    }
    
    /**
     * 自动发送响应（类似Spring MVC的自动序列化）
     */
    private void sendAutomaticResponse(HandlerMethod handlerMethod, Object result, WebSocketSession session) {
        try {
            WsSession wsSession = makeLocalSession(session);
            if (result instanceof WsResponseEntity) {
                // Controller返回了WsResponseEntity，直接发送（开发者控制了协议元数据）
                ((WsResponseEntity<?>) result).invokeSend(wsSession);
                log.debug("发送WsResponseEntity成功: {} -> {}", handlerMethod.getInterfaceName(), result.getClass().getSimpleName());
            } else {
                // Controller返回了业务对象，框架自动包装为WsResponseEntity发送
                WsResponseEntity.ok(result).invokeSend(wsSession);
                log.debug("自动发送业务对象成功: {} -> {}", handlerMethod.getInterfaceName(), result.getClass().getSimpleName());
            }
            
        } catch (Exception e) {
            log.error("自动发送响应失败: {}", handlerMethod.getInterfaceName(), e);
        }
    }
    
    /**
     * 发送错误响应
     */
    private void sendErrorResponse(WebSocketDataWrapper<?> webSocketDataWrapper, String errorMessage, WebSocketSession session) {
        try {
            // 使用WebSocketDataWrapper创建标准化的错误响应
            WebSocketDataWrapper<String> errorResponse = WebSocketDataWrapper.failure(
                "error", // 使用通用的错误接口名
                errorMessage,
                "500"
            );
            WsResponseEntity.ok(errorResponse).invokeSend(makeLocalSession(session));
            log.debug("发送错误响应成功: {}", errorMessage);
        } catch (Exception e) {
            log.error("发送错误响应失败", e);
        }
    }

    /**
     * 发送未找到处理器(404)响应
     */
    private void sendNotFoundResponse(String interfaceName, WebSocketSession session) {
        try {
            String iface = interfaceName != null ? interfaceName : "unknown";
            WebSocketDataWrapper<String> notFound = WebSocketDataWrapper.failure(
                iface,
                "未找到对应的 WebSocket 处理器",
                "404"
            );
            WsResponseEntity.ok(notFound).invokeSend(makeLocalSession(session));
            log.debug("发送404响应: {}", iface);
        } catch (Exception e) {
            log.error("发送404响应失败", e);
        }
    }

    private WsSession makeLocalSession(WebSocketSession session) {
        return new LocalWsSession(session, getCurrentInstanceId());
    }

    private String getCurrentInstanceId() {
        String app = System.getProperty("spring.application.name", "mall-chat");
        String port = System.getProperty("server.port", "8080");
        String configured = System.getProperty("websocket.instance.id");
        if (configured != null && !configured.isEmpty()) return configured;
        return app + "-" + port;
    }
    
    /**
     * 获取所有已注册的接口名称
     */
    public java.util.Set<String> getRegisteredInterfaces() {
        return handlerMapping.getRegisteredInterfaces();
    }
}
