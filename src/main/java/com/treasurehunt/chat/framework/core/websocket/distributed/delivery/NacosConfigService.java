package com.treasurehunt.chat.framework.core.websocket.distributed.delivery;

import com.alibaba.nacos.api.config.annotation.NacosConfigListener;
import com.alibaba.nacos.api.config.annotation.NacosValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Nacos 配置服务（使用 Spring Cloud Alibaba 注解方式）
 * 
 * @author gaga
 * @since 2025-10-06
 */
@Slf4j
@Component
public class NacosConfigService {

    // 重试配置缓存
    private final Map<String, String> configCache = new ConcurrentHashMap<>();

    // 从 Nacos 获取重试配置
    @NacosValue(value = "${notification.retry.config:}", autoRefreshed = true)
    private String retryConfigJson;

    @PostConstruct
    public void init() {
        log.info("Nacos ConfigService initialized");
        loadRetryConfig();
    }

    /**
     * 监听 Nacos 配置变化
     */
    @NacosConfigListener(dataId = "notification-retry-config", groupId = "DEFAULT_GROUP")
    public void onRetryConfigChange(String newConfig) {
        log.info("重试配置发生变化，重新加载");
        configCache.put("notification-retry-config", newConfig);
    }

    /**
     * 获取配置
     * @param dataId 配置ID
     * @param group 组名
     * @param timeoutMs 超时时间
     * @return 配置内容
     */
    public String getConfig(String dataId, String group, long timeoutMs) {
        // 先从缓存获取
        String config = configCache.get(dataId);
        if (config != null) {
            return config;
        }

        // 如果是重试配置，返回注解注入的值
        if ("notification-retry-config".equals(dataId)) {
            return retryConfigJson;
        }

        return null;
    }

    /**
     * 添加配置监听器（Spring Cloud Alibaba 方式不需要手动添加）
     * @param dataId 配置ID
     * @param group 组名
     * @param listener 监听器
     */
    public void addListener(String dataId, String group, Object listener) {
        log.debug("Config listener added for dataId: {} (Spring Cloud Alibaba auto-managed)", dataId);
    }

    /**
     * 加载重试配置
     */
    private void loadRetryConfig() {
        if (retryConfigJson != null && !retryConfigJson.isEmpty()) {
            configCache.put("notification-retry-config", retryConfigJson);
            log.info("重试配置加载完成");
        } else {
            log.info("使用默认重试配置");
        }
    }
}
