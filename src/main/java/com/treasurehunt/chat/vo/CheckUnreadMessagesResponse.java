package com.treasurehunt.chat.vo;

import lombok.Data;
import lombok.Builder;
import java.util.List;
import java.util.Map;

/**
 * 检查未读消息响应
 * 对应WebSocketEnvelope中interface="/checkUnreadMessages"时的payload内容
 */
@Data
@Builder
public class CheckUnreadMessagesResponse {
    
    /**
     * 是否有未读消息
     */
    private Boolean hasUnreadMessages;
    
    /**
     * 总的未读消息数
     */
    private Integer totalUnreadCount;
    
    /**
     * 各会话的未读消息数
     * Key: conversationId, Value: 该会话的未读消息数
     */
    private Map<String, Integer> conversationUnreadCounts;
    
    /**
     * 会话列表（包含最后一条消息和未读消息数）
     */
    private List<ConversationInfo> conversations;
}
