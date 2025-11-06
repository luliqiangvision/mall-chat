package com.treasurehunt.chat.framework.core.websocket.mvc.handler;

import com.treasurehunt.chat.framework.core.websocket.mvc.annotation.WsRequestMapping;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;

/**
 * WebSocket处理器方法
 * 封装了控制器方法和其元数据，性能优化版本
 */
public class HandlerMethod {
    
    private final Object bean;
    private final Method method;
    private final MethodHandle methodHandle;
    private final WsRequestMapping mappingAnnotation;
    private final ParameterInfo[] parameters;
    private final String interfaceName;
    private final String responseInterfaceName;
    
    public HandlerMethod(Object bean, Method method, MethodHandle methodHandle, 
                       WsRequestMapping mappingAnnotation, ParameterInfo[] parameters) {
        this.bean = bean;
        this.method = method;
        this.methodHandle = methodHandle;
        this.mappingAnnotation = mappingAnnotation;
        this.parameters = parameters;
        this.interfaceName = mappingAnnotation.value();
        this.responseInterfaceName = determineResponseInterfaceName();
    }
    
    /**
     * 使用MethodHandle调用方法，性能优于反射
     * @return 方法调用的返回值
     */
    public Object invoke(Object[] args) throws Throwable {
        return methodHandle.invokeWithArguments(args);
    }
    
    /**
     * 确定响应接口名称
     */
    private String determineResponseInterfaceName() {
        // 默认规则：rply + 首字母大写
        return "rply" + interfaceName.substring(0, 1).toUpperCase() + 
               interfaceName.substring(1);
    }
    
    // Getters
    public Object getBean() { return bean; }
    public Method getMethod() { return method; }
    public MethodHandle getMethodHandle() { return methodHandle; }
    public WsRequestMapping getMappingAnnotation() { return mappingAnnotation; }
    public ParameterInfo[] getParameters() { return parameters; }
    public String getInterfaceName() { return interfaceName; }
    public String getResponseInterfaceName() { return responseInterfaceName; }
    
    /**
     * 参数信息
     */
    public static class ParameterInfo {
        private final Class<?> type;
        private final boolean isWsPayload;
        private final Class<?> payloadType;
        private final int index;
        
        public ParameterInfo(Class<?> type, boolean isWsPayload, Class<?> payloadType, int index) {
            this.type = type;
            this.isWsPayload = isWsPayload;
            this.payloadType = payloadType;
            this.index = index;
        }
        
        public Class<?> getType() { return type; }
        public boolean isWsPayload() { return isWsPayload; }
        public Class<?> getPayloadType() { return payloadType; }
        public int getIndex() { return index; }
    }
}
