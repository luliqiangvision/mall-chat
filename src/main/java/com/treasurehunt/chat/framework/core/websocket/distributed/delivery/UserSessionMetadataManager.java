package com.treasurehunt.chat.framework.core.websocket.distributed.delivery;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户会话元数据管理器（基于Redis实现）
 * 
 * 职责：
 * - 维护用户连接元数据：用户ID -> 实例地址 + 会话列表
 * - 支持心跳续期机制
 * - 提供用户在线状态查询
 * - 支持多实例间的数据同步
 * 
 * @author gaga
 * @since 2025-10-06
 */
@Slf4j
@Component
public class UserSessionMetadataManager {

    @Autowired
    private StatefulRedisConnection<String, String> redisConnection;

    // 本机会话缓存（用于快速查找WebSocketSession对象）
    private static final ConcurrentHashMap<String, WebSocketSession> LOCAL_SESSIONS = new ConcurrentHashMap<>();
    
    // Redis Key 前缀
    private static final String USER_SESSIONS_KEY = "user:sessions:";
    private static final String USER_INSTANCE_KEY = "user:instance:";
    
    // TTL 设置
    private static final long SESSION_TTL_MINUTES = 5;

    /**
     * 注册用户会话元数据（连接建立时调用）
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @param instanceAddress 实例地址 (IP:Port)
     */
    public void registerUserSession(String userId, String sessionId, String instanceAddress) {
        if (userId != null && sessionId != null && instanceAddress != null) {
            try {
                RedisCommands<String, String> commands = redisConnection.sync();
                // 1. 记录用户有哪些会话
                commands.sadd(USER_SESSIONS_KEY + userId, sessionId);
                commands.expire(USER_SESSIONS_KEY + userId, SESSION_TTL_MINUTES * 60);
                // 2. 记录用户连接的实例地址
                commands.setex(USER_INSTANCE_KEY + userId, SESSION_TTL_MINUTES * 60, instanceAddress);
                log.debug("Registered user session metadata: userId={}, sessionId={}, instanceAddress={}", userId, sessionId, instanceAddress);
            } catch (Exception e) {
                log.error("Failed to register user session metadata: userId={}, sessionId={}, instanceAddress={}", 
                    userId, sessionId, instanceAddress, e);
            }
        }
    }

    /**
     * 心跳续期（客户端发送心跳时调用）
     * @param userId 用户ID
     * @param sessionId 当前会话ID
     * @param instanceAddress 当前实例地址 (IP:Port)
     */
    public void heartbeat(String userId, String sessionId, String instanceAddress) {
        if (userId != null && sessionId != null && instanceAddress != null) {
            try {
                // 1. 获取旧的会话列表
                RedisCommands<String, String> commands = redisConnection.sync();
                Set<String> oldSessions = commands.smembers(USER_SESSIONS_KEY + userId);
                
                // 2. 更新用户会话集合（添加新会话，移除旧会话）
                if (oldSessions != null && !oldSessions.isEmpty()) {
                    // 移除所有旧会话
                    for (String s : oldSessions) {
                        commands.srem(USER_SESSIONS_KEY + userId, s);
                    }
                }
                
                // 3. 添加新会话
                commands.sadd(USER_SESSIONS_KEY + userId, sessionId);
                commands.expire(USER_SESSIONS_KEY + userId, SESSION_TTL_MINUTES * 60);
                
                // 4. 更新用户实例映射
                commands.setex(USER_INSTANCE_KEY + userId, SESSION_TTL_MINUTES * 60, instanceAddress);
                
                log.debug("Heartbeat processed: userId={}, sessionId={}, instanceAddress={}", userId, sessionId, instanceAddress);
            } catch (Exception e) {
                log.error("Failed to process heartbeat: userId={}, sessionId={}, instanceAddress={}", userId, sessionId, instanceAddress, e);
            }
        }
    }

    /**
     * 移除用户会话元数据（连接断开时调用）
     * @param userId 用户ID
     * @param sessionId 会话ID
     */
    public void removeUserSession(String userId, String sessionId) {
        if (userId != null && sessionId != null) {
            try {
                // 1. 从用户会话集合中移除
                RedisCommands<String, String> commands = redisConnection.sync();
                commands.srem(USER_SESSIONS_KEY + userId, sessionId);
                // 2. 如果用户没有其他会话，删除用户实例映射
                Long remainingSessions = commands.scard(USER_SESSIONS_KEY + userId);
                if (remainingSessions == null || remainingSessions == 0) {
                    commands.del(USER_SESSIONS_KEY + userId);
                    commands.del(USER_INSTANCE_KEY + userId);
                }
                
                log.debug("Removed user session metadata: userId={}, sessionId={}", userId, sessionId);
            } catch (Exception e) {
                log.error("Failed to remove user session metadata: userId={}, sessionId={}", userId, sessionId, e);
            }
        }
    }

    /**
     * 注册本机会话
     * @param session WebSocket会话
     */
    public void registerLocalSession(WebSocketSession session) {
        if (session != null) {
            LOCAL_SESSIONS.put(session.getId(), session);
            log.debug("Registered local session: {}", session.getId());
        }
    }

    /**
     * 移除本机会话
     * @param sessionId 会话ID
     */
    public void removeLocalSession(String sessionId) {
        if (sessionId != null) {
            LOCAL_SESSIONS.remove(sessionId);
            log.debug("Removed local session: {}", sessionId);
        }
    }

    /**
     * 根据用户ID获取会话ID集合（仅本机）
     * @param userId 用户ID
     * @return 会话ID集合
     */
    public Set<String> getSessionIdsByUserId(String userId) {
        if (userId == null) {
            return null;
        }
        try {
            RedisCommands<String, String> commands = redisConnection.sync();
            Set<String> sessionObjects = commands.smembers(USER_SESSIONS_KEY + userId);
            if (sessionObjects == null || sessionObjects.isEmpty()) {
                return null;
            }
            // 过滤出本机存在的会话
            return sessionObjects.stream()
                .map(Object::toString)
                .filter(LOCAL_SESSIONS::containsKey)
                .collect(java.util.stream.Collectors.toSet());
                
        } catch (Exception e) {
            log.error("Failed to get session IDs by user ID: {}", userId, e);
            return null;
        }
    }

    /**
     * 获取用户连接的实例地址
     * TODO 需要注意的是客服的id不能跟客户的冲突,也就是两个账号体系,这部分现在还没有做区分
     * @param userId 用户ID
     * @return 实例地址 (IP:Port)，如果用户离线则返回null
     */
    public String getInstanceAddress(String userId) {
        if (userId == null) {
            return null;
        }
        
        try {
            RedisCommands<String, String> commands = redisConnection.sync();
            String instanceAddress = commands.get(USER_INSTANCE_KEY + userId);
            return instanceAddress != null ? instanceAddress : null;
        } catch (Exception e) {
            log.error("Failed to get instance address for user: {}", userId, e);
            return null;
        }
    }

    /**
     * 获取本机会话
     * @param sessionId 会话ID
     * @return WebSocket会话
     */
    public WebSocketSession getLocalSession(String sessionId) {
        return sessionId == null ? null : LOCAL_SESSIONS.get(sessionId);
    }

    /**
     * 检查用户是否在线
     * @param userId 用户ID
     * @return 是否在线
     */
    public boolean isUserOnline(String userId) {
        try {
            RedisCommands<String, String> commands = redisConnection.sync();
            Long count = commands.scard(USER_SESSIONS_KEY + userId);
            return count != null && count > 0;
        } catch (Exception e) {
            log.error("Failed to check user online status: {}", userId, e);
            return false;
        }
    }
}
