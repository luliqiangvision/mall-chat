package com.treasurehunt.chat.framework.core.websocket.distributed.spi;

import java.util.concurrent.CompletableFuture;

import com.treasurehunt.chat.framework.core.websocket.distributed.delivery.NotificationMessage;
import com.treasurehunt.chat.framework.core.websocket.distributed.delivery.SendResult;

/**
 * 服务器间通信协议接口
 * 
 * 设计理念：
 * - 可插拔：支持多种通信协议（WebSocket、HTTP/2等）
 * - 框架无关：不依赖Spring WebSocket等特定框架
 * - 业务无感知：业务层不需要知道底层实现细节
 * - 通用性：可以给其他项目使用
 * 
 * @author gaga
 * @since 2025-01-24
 */
public interface ServerCommProtocol {
    
    /**
     * 发送消息到目标实例
     * 
     * @param targetInstanceAddress 目标实例地址 (IP:Port)
     * @param message 消息内容
     * @return 发送结果（包含结果码与原因）
     */
    CompletableFuture<SendResult> sendMessage(String targetInstanceAddress, NotificationMessage message);
    
    /**
     * 初始化协议
     * 在协议被激活时调用
     */
    void initialize();
    
    /**
     * 关闭协议
     * 在协议被停用时调用
     */
    void shutdown();
    
    /**
     * 健康检查
     * 
     * @return true表示协议健康，false表示有问题
     */
    boolean isHealthy();
    
    /**
     * 获取协议名称
     * 
     * @return 协议名称，如 "websocket", "http2"
     */
    String getProtocolName();
    
    /**
     * 检查是否支持目标实例
     * 用于避免自己连接自己
     * 
     * @param targetInstanceAddress 目标实例地址
     * @return true表示支持连接，false表示不支持（如自己连接自己）
     */
    boolean supportsTarget(String targetInstanceAddress);
}
