package com.treasurehunt.chat.framework.core.websocket.distributed.spi;

import java.util.Set;

/**
 * 实例注册SPI接口
 * 用于管理WebSocket服务实例的注册和发现
 * 默认实现：Nacos
 */
public interface InstanceRegistry {
    
    /**
     * 注册实例
     * @param instanceId 实例ID
     * @param instanceInfo 实例信息（IP、端口等）
     */
    void registerInstance(String instanceId, String instanceInfo);
    
    /**
     * 注销实例
     * @param instanceId 实例ID
     */
    void unregisterInstance(String instanceId);
    
    /**
     * 获取所有活跃实例
     * @return 活跃实例ID集合
     */
    Set<String> getActiveInstances();
    
    /**
     * 获取当前实例ID
     * @return 当前实例ID
     */
    String getCurrentInstanceId();
    
    /**
     * 获取实例信息
     * @param instanceId 实例ID
     * @return 实例信息，如果实例不存在返回null
     */
    default String getInstanceInfo(String instanceId) {
        return null; // 默认实现，子类可重写
    }
    
    /**
     * 检查实例是否活跃
     * @param instanceId 实例ID
     * @return 是否活跃
     */
    default boolean isInstanceActive(String instanceId) {
        return getActiveInstances().contains(instanceId);
    }
    
    /**
     * 获取活跃实例数量
     * @return 活跃实例数量
     */
    default int getActiveInstanceCount() {
        return getActiveInstances().size();
    }
}
