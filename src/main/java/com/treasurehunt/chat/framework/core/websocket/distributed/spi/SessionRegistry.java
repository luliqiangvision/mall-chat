package com.treasurehunt.chat.framework.core.websocket.distributed.spi;

import java.util.Set;

/**
 * 会话注册SPI接口
 * 用于管理WebSocket会话与实例的映射关系
 * 默认实现：Redis
 */
public interface SessionRegistry {
    
    /**
     * 注册会话
     * @param sessionId WebSocket会话ID
     * @param instanceId 实例ID
     */
    void registerSession(String sessionId, String instanceId);
    
    /**
     * 注销会话
     * @param sessionId WebSocket会话ID
     */
    void unregisterSession(String sessionId);
    
    /**
     * 获取会话所在实例ID
     * @param sessionId WebSocket会话ID
     * @return 实例ID，如果会话不存在返回null
     */
    String getInstanceId(String sessionId);
    
    /**
     * 检查会话是否在线
     * @param sessionId WebSocket会话ID
     * @return 是否在线
     */
    default boolean isSessionOnline(String sessionId) {
        return getInstanceId(sessionId) != null;
    }
    
    /**
     * 获取所有在线会话数量
     * @return 在线会话数量
     */
    default int getOnlineSessionCount() {
        return 0; // 默认实现，子类可重写
    }

    /**
     * 刷新会话 TTL（秒）。默认空实现，具体存储实现可覆盖。
     */
    default void refreshSessionTtl(String sessionId, long seconds) {
        // no-op by default
    }
}
