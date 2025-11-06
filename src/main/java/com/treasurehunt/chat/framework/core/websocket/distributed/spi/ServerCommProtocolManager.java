package com.treasurehunt.chat.framework.core.websocket.distributed.spi;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.treasurehunt.chat.framework.core.websocket.distributed.delivery.NotificationMessage;
import com.treasurehunt.chat.framework.core.websocket.distributed.delivery.SendResult;

/**
 * 服务器间通信协议管理器
 * 
 * 职责：
 * - 管理可插拔的通信协议实现
 * - 根据配置选择激活的协议
 * - 提供统一的通信接口给业务层
 * - 支持协议的热切换（重启后生效）
 * 
 * 设计理念：
 * - 业务层完全无感知底层协议实现
 * - 框架层可以独立维护和升级
 * - 支持多种协议，便于性能对比和选择
 * 
 * @author gaga
 * @since 2025-01-24
 */
@Slf4j
@Component
public class ServerCommProtocolManager {
    
    @Autowired
    private List<ServerCommProtocol> protocols;
    
    @Value("${websocket.distributed.server-comm.protocol:websocket}")
    private String activeProtocolType;
    
    @Value("${websocket.distributed.server-comm.enabled:true}")
    private boolean enabled;
    
    private ServerCommProtocol activeProtocol;
    
    @PostConstruct
    public void initialize() {
        if (!enabled) {
            log.info("Server communication is disabled");
            return;
        }
        
        log.info("Initializing server communication protocol manager");
        log.info("Available protocols: {}", protocols.stream()
            .map(ServerCommProtocol::getProtocolName)
            .toList());
        
        // 根据配置选择协议
        activeProtocol = protocols.stream()
            .filter(protocol -> protocol.getProtocolName().equals(activeProtocolType))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "No protocol found for type: " + activeProtocolType + 
                ", available: " + protocols.stream()
                    .map(ServerCommProtocol::getProtocolName)
                    .toList()));
        
        log.info("Selected protocol: {}", activeProtocol.getProtocolName());
        
        // 初始化选中的协议
        try {
            activeProtocol.initialize();
            log.info("Server communication protocol initialized successfully: {}", activeProtocol.getProtocolName());
        } catch (Exception e) {
            log.error("Failed to initialize server communication protocol: {}", activeProtocol.getProtocolName(), e);
            throw new RuntimeException("Failed to initialize server communication protocol", e);
        }
    }
    
    @PreDestroy
    public void shutdown() {
        if (activeProtocol != null) {
            log.info("Shutting down server communication protocol: {}", activeProtocol.getProtocolName());
            try {
                activeProtocol.shutdown();
            } catch (Exception e) {
                log.error("Error shutting down server communication protocol", e);
            }
        }
    }
    
    /**
     * 发送消息到目标实例
     * 
     * @param targetInstanceAddress 目标实例地址 (IP:Port)
     * @param message 消息内容
     * @return 发送结果
     */
    public CompletableFuture<SendResult> sendMessage(String targetInstanceAddress, NotificationMessage message) {
        if (!enabled || activeProtocol == null) {
            log.debug("Server communication is disabled or not initialized");
            return CompletableFuture.completedFuture(SendResult.fail(SendResult.SendCode.POOL_UNAVAILABLE, "Protocol not initialized", null));
        }
        
        return activeProtocol.sendMessage(targetInstanceAddress, message);
    }
    
    /**
     * 检查协议是否健康
     */
    public boolean isHealthy() {
        return enabled && activeProtocol != null && activeProtocol.isHealthy();
    }
    
    /**
     * 获取当前激活的协议名称
     */
    public String getActiveProtocolName() {
        return activeProtocol != null ? activeProtocol.getProtocolName() : "none";
    }
    
    /**
     * 检查是否支持目标实例
     */
    public boolean supportsTarget(String targetInstanceAddress) {
        return enabled && activeProtocol != null && activeProtocol.supportsTarget(targetInstanceAddress);
    }
    
    /**
     * 获取所有可用的协议
     */
    public List<String> getAvailableProtocols() {
        return protocols.stream()
            .map(ServerCommProtocol::getProtocolName)
            .toList();
    }
}
