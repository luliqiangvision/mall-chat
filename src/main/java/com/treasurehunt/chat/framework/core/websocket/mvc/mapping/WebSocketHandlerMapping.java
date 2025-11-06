package com.treasurehunt.chat.framework.core.websocket.mvc.mapping;

import com.treasurehunt.chat.framework.core.websocket.mvc.annotation.WsRequestBody;
import com.treasurehunt.chat.framework.core.websocket.mvc.annotation.WsRequestMapping;
import com.treasurehunt.chat.framework.core.websocket.mvc.handler.HandlerMethod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import com.treasurehunt.chat.framework.core.websocket.mvc.annotation.WsController;
import com.treasurehunt.chat.framework.core.websocket.mvc.config.EnableWebSocketMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.PostConstruct;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * WebSocket处理器映射
 * 负责扫描和注册@WsRequestMapping方法，类似Spring MVC的HandlerMapping
 */
@Slf4j
@Component("customWebSocketHandlerMapping")
public class WebSocketHandlerMapping {
    
    @Autowired
    private ApplicationContext applicationContext;
    
    // interfaceName -> HandlerMethod 映射表（性能优化：启动时就建立缓存）
    private final Map<String, HandlerMethod> handlerMap = new HashMap<>();
    
    /**
     * 初始化所有处理器映射
     */
    @PostConstruct
    public void initHandlerMappings() {
        log.info("开始初始化 WebSocket 处理器映射");

        // 仅获取标注了 @WsController 的 Bean
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(WsController.class);

        // 计算允许的基础包（来自 @EnableWebSocketMvc 注解）
        Set<String> allowedBasePackages = resolveEnabledBasePackages();

        for (Object bean : beans.values()) {
            Class<?> beanClass = bean.getClass();

            if (allowedBasePackages.isEmpty() || isInAllowedPackages(beanClass, allowedBasePackages)) {
                scanController(bean, beanClass.getSimpleName());
            }
        }
        
        log.info("WebSocket 处理器映射初始化完成，共注册 {} 个处理器", handlerMap.size());
    }

    private Set<String> resolveEnabledBasePackages() {
        Set<String> basePackages = new HashSet<>();
        Map<String, Object> configs = applicationContext.getBeansWithAnnotation(EnableWebSocketMvc.class);
        for (Object configBean : configs.values()) {
            EnableWebSocketMvc ann = configBean.getClass().getAnnotation(EnableWebSocketMvc.class);
            if (ann != null) {
                for (String p : ann.value()) {
                    if (p != null && !p.isEmpty()) basePackages.add(p);
                }
                for (String p : ann.basePackages()) {
                    if (p != null && !p.isEmpty()) basePackages.add(p);
                }
                for (Class<?> c : ann.basePackageClasses()) {
                    if (c != null && c.getPackage() != null) basePackages.add(c.getPackage().getName());
                }
            }
        }
        return basePackages;
    }

    private boolean isInAllowedPackages(Class<?> beanClass, Set<String> allowedBasePackages) {
        Package pkg = beanClass.getPackage();
        String name = pkg != null ? pkg.getName() : "";
        for (String base : allowedBasePackages) {
            if (name.startsWith(base)) return true;
        }
        return false;
    }
    
    /**
     * 扫描控制器中的方法
     */
    private void scanController(Object controller, String controllerName) {
        Class<?> controllerClass = controller.getClass();
        
        // 获取控制器上的路径前缀
        WsController controllerAnnotation = controllerClass.getAnnotation(WsController.class);
        String controllerPrefix = controllerAnnotation != null ? controllerAnnotation.value() : "";
        
        Method[] methods = controllerClass.getDeclaredMethods();
        
        for (Method method : methods) {
            WsRequestMapping annotation = method.getAnnotation(WsRequestMapping.class);
            if (annotation != null) {
                String methodPath = annotation.value();
                
                // 拼接控制器前缀和方法路径
                String interfaceName = buildPath(controllerPrefix, methodPath);
                
                // 验证方法签名
                if (!isValidMethod(method)) {
                    log.error("跳过无效的 WebSocket 处理器方法: {}.{}, 参数类型不符合要求",
                            controllerName, method.getName());
                    continue;
                }
                
                // 检查路径冲突：如果已存在相同的路径，直接报错，不允许启动
                HandlerMethod existingHandler = handlerMap.get(interfaceName);
                if (existingHandler != null) {
                    String existingControllerName = existingHandler.getBean().getClass().getSimpleName();
                    String existingMethodName = existingHandler.getMethod().getName();
                    String errorMessage = String.format(
                        "WebSocket路径冲突：路径 '%s' 已被注册。" +
                        "冲突信息：已存在 -> %s.%s，当前 -> %s.%s。" +
                        "请修改其中一个控制器的路径，确保路径唯一。",
                        interfaceName,
                        existingControllerName, existingMethodName,
                        controllerName, method.getName()
                    );
                    log.error(errorMessage);
                    throw new IllegalStateException(errorMessage);
                }
                
                try {
                    // 分析参数信息（性能优化：提前分析，避免运行时解析）
                    HandlerMethod.ParameterInfo[] parameterInfos = analyzeParameters(method);
                    
                    // 创建 MethodHandle（性能优化）
                    MethodType methodType = MethodType.methodType(method.getReturnType(), getParameterTypes(parameterInfos));
                    MethodHandle methodHandle = MethodHandles.lookup()
                        .findVirtual(controllerClass, method.getName(), methodType)
                        .bindTo(controller);
                    
                    HandlerMethod handlerMethod = new HandlerMethod(
                        controller, method, methodHandle, annotation, parameterInfos
                    );
                    
                    handlerMap.put(interfaceName, handlerMethod);
                    log.info("注册 WebSocket 处理器: {} -> {}.{}", 
                            interfaceName, controllerName, method.getName());
                } catch (Exception e) {
                    log.error("创建处理器失败: {}.{}", controllerName, method.getName(), e);
                    throw new IllegalStateException("WebSocket处理器注册失败: " + controllerName + "." + method.getName(), e);
                }
            }
        }
    }
    
    /**
     * 构建完整的路径
     * 拼接控制器前缀和方法路径
     * 
     * @param controllerPrefix 控制器路径前缀
     * @param methodPath 方法路径
     * @return 拼接后的完整路径
     */
    private String buildPath(String controllerPrefix, String methodPath) {
        // 如果前缀为空，直接使用方法路径
        if (controllerPrefix == null || controllerPrefix.isEmpty()) {
            return methodPath;
        }
        
        // 规范化路径：确保前缀以/开头，不以/结尾
        String normalizedPrefix = controllerPrefix.startsWith("/") 
            ? controllerPrefix 
            : "/" + controllerPrefix;
        normalizedPrefix = normalizedPrefix.endsWith("/") && normalizedPrefix.length() > 1
            ? normalizedPrefix.substring(0, normalizedPrefix.length() - 1)
            : normalizedPrefix;
        
        // 规范化方法路径：确保以/开头
        String normalizedMethodPath = methodPath.startsWith("/") 
            ? methodPath 
            : "/" + methodPath;
        
        // 拼接路径
        return normalizedPrefix + normalizedMethodPath;
    }
    
    /**
     * 验证方法签名是否符合WebSocket处理器要求
     */
    private boolean isValidMethod(Method method) {
        Class<?>[] paramTypes = method.getParameterTypes();
        
        // 至少有WebSocketSession参数，并且是最后一个参数
        if (paramTypes.length < 1 || paramTypes[paramTypes.length - 1] != WebSocketSession.class) {
            return false;
        }
        
        // 分析参数注解
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length - 1; i++) { // 排除最后一个WebSocketSession参数
            Parameter param = parameters[i];
            WsRequestBody payloadAnnotation = param.getAnnotation(WsRequestBody.class);
            
            // 没有@WsRequestBody注解的参数无效
            if (payloadAnnotation == null) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 分析参数信息（性能优化：启动时预分析）
     */
    private HandlerMethod.ParameterInfo[] analyzeParameters(Method method) {
        Parameter[] parameters = method.getParameters();
        HandlerMethod.ParameterInfo[] parameterInfos = new HandlerMethod.ParameterInfo[parameters.length];
        
        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            Class<?> paramType = param.getType();
            
            if (paramType == WebSocketSession.class) {
                // WebSocketSession参数
                parameterInfos[i] = new HandlerMethod.ParameterInfo(
                    paramType, false, null, i);
            } else {
                // @WsPayload参数
                WsRequestBody payloadAnnotation = param.getAnnotation(WsRequestBody.class);
                Class<?> payloadType = payloadAnnotation.value() != Void.class 
                    ? payloadAnnotation.value() : paramType;
                    
                parameterInfos[i] = new HandlerMethod.ParameterInfo(
                    paramType, true, payloadType, i);
            }
        }
        
        return parameterInfos;
    }
    
    /**
     * 获取参数类型数组（用于MethodType）
     */
    private Class<?>[] getParameterTypes(HandlerMethod.ParameterInfo[] parameterInfos) {
        Class<?>[] types = new Class<?>[parameterInfos.length];
        for (int i = 0; i < parameterInfos.length; i++) {
            types[i] = parameterInfos[i].getPayloadType() != null 
                ? parameterInfos[i].getPayloadType() 
                : parameterInfos[i].getType();
        }
        return types;
    }
    
    /**
     * 根据接口名称获取处理器方法
     */
    public HandlerMethod getHandler(String interfaceName) {
        return handlerMap.get(interfaceName);
    }
    
    /**
     * 获取所有已注册的接口名称
     */
    public java.util.Set<String> getRegisteredInterfaces() {
        return handlerMap.keySet();
    }
}
