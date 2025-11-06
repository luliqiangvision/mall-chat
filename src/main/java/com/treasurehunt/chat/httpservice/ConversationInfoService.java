package com.treasurehunt.chat.httpservice;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.treasurehunt.chat.domain.ChatConversationDO;
import com.treasurehunt.chat.domain.ChatMessageDO;
import com.treasurehunt.chat.domain.UserConversationReadDO;
import com.treasurehunt.chat.mapper.ChatMessageMapper;
import com.treasurehunt.chat.mapper.UserConversationReadMapper;
import com.treasurehunt.chat.utils.Conver;
import com.treasurehunt.chat.vo.ConversationInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 会话信息工具类
 * 提供批量获取会话信息的通用方法
 */
@Slf4j
@Component
public class ConversationInfoService {

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private UserConversationReadMapper userConversationReadMapper;

    /**
     * 批量获取会话信息（包含最后一条消息和未读消息数）
     * 
     * @param conversations 会话列表
     * @param id 用户ID或客服ID（用于计算未读消息数）
     * @param isAgent true表示是客服ID（查询agent_id字段），false表示是客户ID（查询user_id字段）
     * @return 会话信息列表
     */
    public List<ConversationInfo> getConversationInfos(List<ChatConversationDO> conversations, String id, boolean isAgent) {
        if (conversations == null || conversations.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<String> conversationIds = conversations.stream()
                .map(ChatConversationDO::getConversationId)
                .collect(Collectors.toList());
        
        // 1. 批量获取每个会话的最后一条消息
        List<ChatMessageDO> lastMessages = chatMessageMapper.selectList(
                new QueryWrapper<ChatMessageDO>()
                        .in("conversation_id", conversationIds)
                        .inSql("server_msg_id", 
                                "SELECT MAX(server_msg_id) FROM chat_message cm2 " +
                                "WHERE cm2.conversation_id = chat_message.conversation_id")
        );
        
        // 2. 构建消息映射
        Map<String, ChatMessageDO> messageMap = lastMessages.stream()
                .collect(Collectors.toMap(
                        ChatMessageDO::getConversationId,
                        message -> message,
                        (existing, replacement) -> existing
                ));
        
        // 3. 批量查询未读消息数（如果提供了id）
        Map<String, Integer> unreadCountMap = new HashMap<>();
        if (id != null) {
            QueryWrapper<UserConversationReadDO> queryWrapper = new QueryWrapper<UserConversationReadDO>()
                    .in("conversation_id", conversationIds);
            
            // 根据isAgent参数决定查询user_id还是agent_id字段
            if (isAgent) {
                // 客服模式：查询agent_id字段（agentId是String类型，需要转换为Long）
                queryWrapper.eq("agent_id", Long.parseLong(id));
            } else {
                // 客户模式：查询user_id字段（userId是String类型，需要转换为Long）
                queryWrapper.eq("user_id", Long.parseLong(id));
            }
            
            List<UserConversationReadDO> readRecords = userConversationReadMapper.selectList(queryWrapper);
            
            Map<String, Long> readPointerMap = readRecords.stream()
                    .collect(Collectors.toMap(
                            UserConversationReadDO::getConversationId,
                            UserConversationReadDO::getLastReadServerMsgId
                    ));
            
            // 计算每个会话的未读消息数
            for (ChatMessageDO message : lastMessages) {
                String convId = message.getConversationId();
                Long lastReadServerMsgId = readPointerMap.getOrDefault(convId, 0L);
                long unreadCount = message.getServerMsgId() - lastReadServerMsgId;
                unreadCountMap.put(convId, Math.max(0, (int) unreadCount));
            }
        }
        
        // 4. 批量转换为VO
        return conversations.stream()
                .map(conv -> {
                    ChatMessageDO lastMessage = messageMap.get(conv.getConversationId());
                    Integer unreadCount = unreadCountMap.getOrDefault(conv.getConversationId(), 0);
                    
                    return Conver.toConversationInfo(
                            conv,
                            lastMessage != null ? lastMessage.getContent() : null,
                            lastMessage != null ? lastMessage.getCreatedAt().getTime() : null,
                            unreadCount
                    );
                })
                .collect(Collectors.toList());
    }
}
