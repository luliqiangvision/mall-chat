package com.treasurehunt.chat.framework.core.websocket.mvc.handler;

import com.treasurehunt.chat.framework.core.websocket.mvc.annotation.WsControllerAdvice;
import com.treasurehunt.chat.framework.core.websocket.mvc.annotation.WsExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket异常处理器解析器
 * 类似Spring MVC的ExceptionHandlerExceptionResolver
 */
@Slf4j
@Component
public class WebSocketExceptionHandlerResolver {
    
    @Autowired
    private ApplicationContext applicationContext;
    
    // 异常类型 -> 处理器方法
    private final Map<Class<?>, ExceptionHandlerInfo> exceptionHandlers = new HashMap<>();
    
    @PostConstruct
    public void initExceptionHandlers() {
        log.info("开始初始化 WebSocket 异常处理器");
        
        // 扫描所有的@WsControllerAdvice类
        Map<String, Object> beans = applicationContext.getBeansOfType(Object.class);
        for (Object bean : beans.values()) {
            Class<?> beanClass = bean.getClass();
            if (beanClass.isAnnotationPresent(WsControllerAdvice.class)) {
                scanExceptionHandlers(bean, beanClass);
            }
        }
        
        log.info("WebSocket 异常处理器初始化完成，共注册 {} 个处理器", exceptionHandlers.size());
    }
    
    private void scanExceptionHandlers(Object bean, Class<?> beanClass) {
        Method[] methods = beanClass.getDeclaredMethods();
        
        for (Method method : methods) {
            WsExceptionHandler annotation = method.getAnnotation(WsExceptionHandler.class);
            if (annotation != null) {
                Class<?>[] exceptionTypes = annotation.value();
                
                for (Class<?> exceptionType : exceptionTypes) {
                    ExceptionHandlerInfo handlerInfo = new ExceptionHandlerInfo(bean, method);
                    exceptionHandlers.put(exceptionType, handlerInfo);
                    log.info("注册异常处理器: {} -> {}.{}", exceptionType.getSimpleName(), 
                            beanClass.getSimpleName(), method.getName());
                }
            }
        }
    }
    
    /**
     * 解析异常处理器
     */
    public ExceptionHandlerInfo resolveExceptionHandler(Throwable exception) {
        Class<?> exceptionClass = exception.getClass();
        
        // 1. 精确匹配
        ExceptionHandlerInfo handlerInfo = exceptionHandlers.get(exceptionClass);
        if (handlerInfo != null) {
            return handlerInfo;
        }
        
        // 2. 继承链匹配
        for (Map.Entry<Class<?>, ExceptionHandlerInfo> entry : exceptionHandlers.entrySet()) {
            if (entry.getKey().isAssignableFrom(exceptionClass)) {
                return entry.getValue();
            }
        }
        
        return null;
    }
    
    /**
     * 异常处理器信息
     */
    public static class ExceptionHandlerInfo {
        private final Object bean;
        private final Method method;
        
        public ExceptionHandlerInfo(Object bean, Method method) {
            this.bean = bean;
            this.method = method;
        }
        
        public Object getBean() { return bean; }
        public Method getMethod() { return method; }
    }
}
