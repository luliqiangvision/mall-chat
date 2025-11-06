package com.treasurehunt.chat.vo;

import lombok.Data;
import lombok.Builder;

import java.util.List;

/**
 * 会话视图VO
 * 用于初始化会话视图时返回每个会话的消息和未读数量
 */
@Data
@Builder
public class ConversationViewVO {
    
    /**
     * 消息列表
     */
    private List<ChatMessage> messages;
    
    /**
     * 未读消息数量
     */
    private Integer unreadCount;
    
    /**
     * 店铺信息
     */
    private MallShopVO shop;
}
