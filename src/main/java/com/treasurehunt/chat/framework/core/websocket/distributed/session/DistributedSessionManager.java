package com.treasurehunt.chat.framework.core.websocket.distributed.session;

/**
 * 分布式会话管理器接口
 * 负责管理WebSocket会话与实例的映射关系
 * 通过SPI抽象，支持可插拔的会话存储实现
 */
public interface DistributedSessionManager {
    
    /**
     * 注册会话,比如注册到redis里
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
    boolean isSessionOnline(String sessionId);
    
    /**
     * 获取在线会话数量
     * @return 在线会话数量
     */
    int getOnlineSessionCount();

    /**
     * 刷新会话 TTL（秒）
     */
    void refreshSessionTtl(String sessionId, long seconds);
}