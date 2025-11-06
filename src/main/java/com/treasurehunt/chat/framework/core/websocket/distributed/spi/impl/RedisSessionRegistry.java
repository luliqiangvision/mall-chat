package com.treasurehunt.chat.framework.core.websocket.distributed.spi.impl;

import com.treasurehunt.chat.framework.core.websocket.distributed.spi.SessionRegistry;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Redis会话注册实现
 * 使用Redis Hash存储用户会话映射关系
 */
@Component
public class RedisSessionRegistry implements SessionRegistry {
    
    @Autowired
    private StatefulRedisConnection<String, String> redisConnection;
    
    private static final String SESSION_PREFIX = "ws:session:";
    private static final String USER_SESSIONS_PREFIX = "ws:user:";

    @Override
    public void registerSession(String sessionId, String instanceId) {
        String sessionKey = SESSION_PREFIX + sessionId;
        // 设置过期时间，默认 300 秒（5 分钟）
        RedisCommands<String, String> commands = redisConnection.sync();
        commands.setex(sessionKey, 300, instanceId);
    }

    /**
     * 注册用户会话到集合（5分钟TTL）
     * @param userId 用户ID
     * @param sessionId 会话ID
     */
    public void registerUserSession(String userId, String sessionId) {
        String userSessionsKey = USER_SESSIONS_PREFIX + userId + ":sessions";
        RedisCommands<String, String> commands = redisConnection.sync();
        commands.sadd(userSessionsKey, sessionId);
        // 设置用户会话集合TTL为300秒（5分钟），与session TTL一致
        commands.expire(userSessionsKey, 300);
    }
    
    @Override
    public void unregisterSession(String sessionId) {
        // 删除会话映射
        String sessionKey = SESSION_PREFIX + sessionId;
        RedisCommands<String, String> commands = redisConnection.sync();
        commands.del(sessionKey);
    }

    
    @Override
    public String getInstanceId(String sessionId) {
        String sessionKey = SESSION_PREFIX + sessionId;
        RedisCommands<String, String> commands = redisConnection.sync();
        String instanceId = commands.get(sessionKey);
        return instanceId;
    }
    
    @Override
    public boolean isSessionOnline(String sessionId) {
        return getInstanceId(sessionId) != null;
    }
    
    @Override
    public int getOnlineSessionCount() {
        // 获取所有会话映射的key
        RedisCommands<String, String> commands = redisConnection.sync();
        List<String> sessionKeys = commands.keys(SESSION_PREFIX + "*");
        return sessionKeys != null ? sessionKeys.size() : 0;
    }

    @Override
    public void refreshSessionTtl(String sessionId, long seconds) {
        String sessionKey = SESSION_PREFIX + sessionId;
        RedisCommands<String, String> commands = redisConnection.sync();
        commands.expire(sessionKey, seconds);
    }
}
