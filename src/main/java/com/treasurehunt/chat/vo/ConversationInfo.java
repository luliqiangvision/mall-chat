package com.treasurehunt.chat.vo;

import lombok.Data;
import lombok.Builder;

/**
 * 会话信息
 */
@Data
@Builder
public class ConversationInfo {
    
    /**
     * 会话ID
     */
    private String conversationId;
    
    /**
     * 客户用户ID
     */
    private String customerId;
    
    /**
     * 客户用户名
     */
    private String customerName;
    
    /**
     * 最后一条消息内容
     */
    private String lastMessage;
    
    /**
     * 最后一条消息时间
     */
    private Long lastMessageTime;
    
    /**
     * 未读消息数
     */
    private Integer unreadCount;
    
    /**
     * 会话状态（active、waiting、closed、deleted_by_customer、deleted_by_agent）
     */
    private String status;
    
    /**
     * 会话创建时间
     */
    private Long createdAt;
    
    /**
     * 多租户标识
     */
    private Long tenantId;
}
