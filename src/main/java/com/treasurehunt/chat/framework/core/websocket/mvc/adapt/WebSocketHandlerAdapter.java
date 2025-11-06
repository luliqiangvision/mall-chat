package com.treasurehunt.chat.framework.core.websocket.mvc.adapt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treasurehunt.chat.framework.core.websocket.mvc.handler.HandlerMethod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/**
 * WebSocket处理器适配器
 * 负责参数绑定和方法调用，类似Spring MVC的HandlerAdapter
 */
@Slf4j
@Component
public class WebSocketHandlerAdapter {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 处理WebSocket请求
     * @param handlerMethod 处理器方法
     * @param request WebSocket请求
     * @param session WebSocket会话
     * @return Controller方法返回值，用于自动响应
     */
    public Object handle(HandlerMethod handlerMethod, Object request, WebSocketSession session) {
        if (handlerMethod == null) {
            log.warn("处理器方法为空");
            return null;
        }
        
        try {
            // 构造参数数组（性能优化：避免动态数组扩展）
            HandlerMethod.ParameterInfo[] parameterInfos = handlerMethod.getParameters();
            Object[] args = new Object[parameterInfos.length];
            
            // 绑定参数（性能优化：提前分析过的参数信息）
            for (int i = 0; i < parameterInfos.length; i++) {
                HandlerMethod.ParameterInfo paramInfo = parameterInfos[i];
                
                if (paramInfo.getType() == WebSocketSession.class) {
                    // WebSocketSession参数
                    args[i] = session;
                } else if (paramInfo.isWsPayload()) {
                    // @WsPayload参数 - 自动类型转换
                    args[i] = convertPayload(request, paramInfo);
                } else {
                    // 其他参数（如果需要的话）
                    args[i] = request;
                }
            }
            
            // 使用MethodHandle调用（性能优化：比反射快）
            Object result = invokeHandlerMethod(handlerMethod, args);
            
            log.debug("成功处理 WebSocket 请求: {}", handlerMethod.getInterfaceName());
            return result;
            
        } catch (Throwable e) {
            log.error("处理WebSocket请求失败: {}", handlerMethod.getInterfaceName(), e);
            throw new RuntimeException("处理器调用失败", e);
        }
    }
    
    /**
     * 调用处理器方法并返回结果
     */
    private Object invokeHandlerMethod(HandlerMethod handlerMethod, Object[] args) throws Throwable {
        return handlerMethod.invoke(args);
    }
    
    /**
     * 转换payload为指定类型（性能优化：使用convertValue比readValue快）
     */
    private Object convertPayload(Object request, HandlerMethod.ParameterInfo paramInfo) throws Exception {
        try {
            // 假设request有getPayload()方法获取原始payload
            Object payload = extractPayload(request);
            
            // 使用Jackson的类型转换（性能优化）
            return objectMapper.convertValue(payload, paramInfo.getPayloadType());
        } catch (Exception e) {
            log.error("转换Payload失败,类型:{}", paramInfo.getPayloadType(), e);
            throw new RuntimeException("Payload转换失败", e);
        }
    }
    
    /**
     * 提取原始payload（假设请求对象有getPayload方法）
     */
    private Object extractPayload(Object request) throws Exception {
        // 这里使用反射调用getPayload方法，实际项目中可以优化
        return request.getClass().getMethod("getPayload").invoke(request);
    }
    
    /**
     * 检查处理器方法是否支持当前请求
     */
    public boolean supports(Object handler) {
        return handler instanceof HandlerMethod;
    }
}
