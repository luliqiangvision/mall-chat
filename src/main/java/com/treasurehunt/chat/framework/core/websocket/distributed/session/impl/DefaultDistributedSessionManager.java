package com.treasurehunt.chat.framework.core.websocket.distributed.session.impl;

import com.treasurehunt.chat.framework.core.websocket.distributed.session.DistributedSessionManager;
import com.treasurehunt.chat.framework.core.websocket.distributed.spi.SessionRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 默认分布式会话管理器实现
 * 通过SPI调用SessionRegistry进行会话管理
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "websocket.distributed.enabled", havingValue = "true")
public class DefaultDistributedSessionManager implements DistributedSessionManager {
    
    @Autowired(required = false)
    private SessionRegistry sessionRegistry;
    
    @Value("${websocket.instance.id:${spring.application.name}-${random.uuid}}")
    private String currentInstanceId;
    
    @Override
    public void registerSession(String sessionId, String instanceId) {
        if (sessionRegistry != null) {
            try {
                sessionRegistry.registerSession(sessionId, instanceId);
                log.debug("分布式会话注册成功: sessionId={}, instanceId={}", sessionId, instanceId);
            } catch (Exception e) {
                log.error("分布式会话注册失败: sessionId={}, instanceId={}", sessionId, instanceId, e);
            }
        } else {
            log.warn("SessionRegistry未配置，跳过分布式会话注册: sessionId={}", sessionId);
        }
    }
    
    @Override
    public void unregisterSession(String sessionId) {
        if (sessionRegistry != null) {
            try {
                sessionRegistry.unregisterSession(sessionId);
                log.debug("分布式会话注销成功: sessionId={}", sessionId);
            } catch (Exception e) {
                log.error("分布式会话注销失败: sessionId={}", sessionId, e);
            }
        } else {
            log.warn("SessionRegistry未配置，跳过分布式会话注销: sessionId={}", sessionId);
        }
    }
    
    @Override
    public String getInstanceId(String sessionId) {
        if (sessionRegistry != null) {
            try {
                return sessionRegistry.getInstanceId(sessionId);
            } catch (Exception e) {
                log.error("获取会话实例ID失败: sessionId={}", sessionId, e);
            }
        }
        return null;
    }
    
    @Override
    public boolean isSessionOnline(String sessionId) {
        return getInstanceId(sessionId) != null;
    }
    
    @Override
    public int getOnlineSessionCount() {
        if (sessionRegistry != null) {
            try {
                return sessionRegistry.getOnlineSessionCount();
            } catch (Exception e) {
                log.error("获取在线会话数量失败", e);
            }
        }
        return 0;
    }

    @Override
    public void refreshSessionTtl(String sessionId, long seconds) {
        if (sessionRegistry != null) {
            try {
                sessionRegistry.refreshSessionTtl(sessionId, seconds);
            } catch (Exception e) {
                log.warn("刷新会话TTL失败: sessionId={}, seconds={}", sessionId, seconds, e);
            }
        }
    }
    
    /**
     * 获取当前实例ID
     * @return 当前实例ID
     */
    public String getCurrentInstanceId() {
        return currentInstanceId;
    }
}
