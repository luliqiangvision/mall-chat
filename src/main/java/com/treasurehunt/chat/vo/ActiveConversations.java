package com.treasurehunt.chat.vo;

import lombok.Data;
import lombok.Builder;
import java.util.List;
import java.util.Map;

/**
 * 获取会话列表响应
 * 对应WebSocketEnvelope中interface="getConversations"时的payload内容
 */
@Data
@Builder
public class ActiveConversations {
    
    /**
     * 会话列表信息（客服的会话列表）
     */
    private List<ConversationInfo> conversations;
    
    /**
     * 总的未读消息数（所有会话的未读消息总和）
     */
    private Integer totalUnreadCount;
    
    /**
     * 各个窗口的未读消息数（按会话ID分组的未读消息统计）
     * Key: conversationId, Value: 该会话的未读消息数
     */
    private Map<String, Integer> conversationUnreadCounts;
    
}
