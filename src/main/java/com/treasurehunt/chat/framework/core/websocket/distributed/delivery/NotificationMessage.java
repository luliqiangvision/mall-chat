package com.treasurehunt.chat.framework.core.websocket.distributed.delivery;

import lombok.Data;

import java.util.Set;

/**
 * 通知消息
 * 
 * Redis Stream 存储大小估算：
 * - 基础字段：serviceType(~10B) + conversationId(~36B) + serverMsgId(~19B) + senderId(~18B) + timestamp(~13B) ≈ 96B
 * - targetUserIds：每个用户ID约18B，假设群聊平均5人 ≈ 90B
 * - Redis Stream 元数据开销：~24B + 字段名开销(~50B) + 字段值开销(~4B×6) ≈ 98B
 * - 总计：约 284B ≈ 0.28KB（文本消息场景）
 * 
 * 注意：实际大小会根据 targetUserIds 数量和用户ID长度变化
 * 
 * @author gaga
 * @since 2025-10-06
 */
@Data
public class NotificationMessage {
    private String serviceType; // 服务类型，用于 Redis Stream 隔离，由业务层设置
    private String conversationId;
    private long serverMsgId;
    private String senderId; // 发送者ID
    private Set<String> targetUserIds; // 目标用户列表
    private long timestamp;
    
    public NotificationMessage() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public NotificationMessage(String serviceType, String conversationId, long serverMsgId, 
                             String senderId, Set<String> targetUserIds) {
        this.serviceType = serviceType;
        this.conversationId = conversationId;
        this.serverMsgId = serverMsgId;
        this.senderId = senderId;
        this.targetUserIds = targetUserIds;
        this.timestamp = System.currentTimeMillis();
    }
}
