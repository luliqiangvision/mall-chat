package com.treasurehunt.chat.framework.core.websocket.mvc.scanner;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * WebSocket MVC配置器
 * 完全类似Spring MVC的WebMvcConfigurer
 * 支持通过配置属性自定义扫描包路径
 */
@Slf4j
@Configuration
@ComponentScan(basePackages = {
    "com.macro.mall.chat.controller",
    "com.macro.mall.chat.framework.core.handler", 
    "com.macro.mall.chat.framework.core.interceptor"
})
public class WebSocketMvcConfigurer {
    
    // 默认扫描包路径
    private static final String[] DEFAULT_SCAN_PACKAGES = {
        "com.macro.mall.chat.controller",
        "com.macro.mall.chat.framework.core.handler",
        "com.macro.mall.chat.framework.core.interceptor"
    };
    
    @Value("${websocket.mvc.scan-packages:}")
    private String[] customScanPackages;
    
    public WebSocketMvcConfigurer() {
        String[] packages = getEffectiveScanPackages();
        log.info("启用WebSocket MVC自动配置，扫描包路径: {}", Arrays.toString(packages));
    }
    
    /**
     * 获取有效的扫描包路径
     * 优先使用配置的包路径，否则使用默认包路径
     */
    public String[] getEffectiveScanPackages() {
        return customScanPackages != null && customScanPackages.length > 0 
            ? customScanPackages 
            : DEFAULT_SCAN_PACKAGES;
    }
}
