package com.treasurehunt.chat.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treasurehunt.chat.domain.ChatMessageDO;
import com.treasurehunt.chat.domain.ChatConversationDO;
import com.treasurehunt.chat.domain.ChatConversationMemberDO;
import com.treasurehunt.chat.domain.MallShopDO;
import com.treasurehunt.chat.vo.ChatMessage;
import com.treasurehunt.chat.vo.ConversationInfo;
import com.treasurehunt.chat.vo.MallShopVO;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

public final class Conver {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private Conver() {}

    public static ChatMessageDO toChatMessageDO(ChatMessage chatMessage, Long serverMsgId) {
        ChatMessageDO.ChatMessageDOBuilder builder = ChatMessageDO.builder();

        if (serverMsgId != null) {
            builder.serverMsgId(serverMsgId);
        }
        if (chatMessage == null) {
            return builder.build();
        }

        if (chatMessage.getClientMsgId() != null) {
            builder.clientMsgId(chatMessage.getClientMsgId());
        }
        if (chatMessage.getConversationId() != null) {
            builder.conversationId(chatMessage.getConversationId());
        }
        if (chatMessage.getType() != null) {
            builder.msgType(chatMessage.getType());
        }
        if (chatMessage.getContent() != null) {
            builder.content(chatMessage.getContent());
        }
        if (chatMessage.getFromUserId() != null) {
            builder.fromUserId(chatMessage.getFromUserId());
        }
        if (chatMessage.getSenderId() != null) {
            builder.senderId(chatMessage.getSenderId());
        }
        if (chatMessage.getStatus() != null) {
            builder.status(chatMessage.getStatus());
        }
        if (chatMessage.getShopId() != null) {
            builder.shopId(chatMessage.getShopId());
        }
        if (chatMessage.getPayload() != null) {
            try {
                builder.payloadJson(OBJECT_MAPPER.writeValueAsString(chatMessage.getPayload()));
            } catch (Exception ignore) {
                // ignore payload serialization errors
            }
        }

        // created_at: 若未显式设置，由于SQL中显式插入了该列，必须在这里填充以避免覆盖数据库默认值为NULL
        // 确保始终设置 created_at，即使客户端的 timestamp 为 null 或格式错误
        Date createdAt = null;
        if (chatMessage != null && chatMessage.getTimestamp() != null) {
            createdAt = chatMessage.getTimestamp();
        }
        // 如果客户端时间戳为 null 或无效，使用当前时间
        if (createdAt == null) {
            createdAt = new java.util.Date();
        }
        builder.createdAt(createdAt);

        return builder.build();
    }

    /**
     * ChatMessageDO 转为 ChatMessage VO
     */
    public static ChatMessage toChatMessage(ChatMessageDO messageDO) {
        if (messageDO == null) {
            return null;
        }

        ChatMessage message = new ChatMessage();
        message.setConversationId(messageDO.getConversationId());
        message.setServerMsgId(messageDO.getServerMsgId());
        message.setClientMsgId(messageDO.getClientMsgId());
        message.setFromUserId(messageDO.getFromUserId());
        message.setSenderId(messageDO.getSenderId());
        message.setType(messageDO.getMsgType());
        message.setContent(messageDO.getContent());
        message.setStatus(messageDO.getStatus());
        
        // 设置时间戳
        if (messageDO.getCreatedAt() != null) {
            message.setTimestamp(messageDO.getCreatedAt());
        }
        
        // 解析payload
        if (messageDO.getPayloadJson() != null && !messageDO.getPayloadJson().isEmpty()) {
            try {
                Object payload = OBJECT_MAPPER.readValue(messageDO.getPayloadJson(), Object.class);
                message.setPayload(payload);
            } catch (Exception e) {
                // payload解析失败，忽略
                message.setPayload(null);
            }
        }

        return message;
    }

    /**
     * ChatConversationDO 转为 ConversationInfo VO
     * 
     * @param conversationDO 会话DO对象
     * @param lastMessage 最后一条消息内容
     * @param lastMessageTime 最后一条消息时间
     * @param unreadCount 未读消息数
     * @return ConversationInfo VO对象
     */
    public static ConversationInfo toConversationInfo(ChatConversationDO conversationDO, 
                                                     String lastMessage, 
                                                     Long lastMessageTime, 
                                                     Integer unreadCount) {
        if (conversationDO == null) {
            return null;
        }

        return ConversationInfo.builder()
                .conversationId(conversationDO.getConversationId())
                .customerId(String.valueOf(conversationDO.getCustomerId()))
                .customerName("客户" + conversationDO.getCustomerId()) // 简化处理
                .lastMessage(lastMessage != null ? lastMessage : "")
                .lastMessageTime(lastMessageTime != null ? lastMessageTime : conversationDO.getCreatedAt().getTime())
                .unreadCount(unreadCount != null ? unreadCount : 0)
                .status(conversationDO.getStatus())
                .createdAt(conversationDO.getCreatedAt().getTime())
                .tenantId(conversationDO.getTenantId())
                .build();
    }
    
    /**
     * MallShopDO 转为 MallShopVO
     * 
     * @param shopDO 店铺DO对象
     * @return MallShopVO对象
     */
    public static MallShopVO toMallShopVO(MallShopDO shopDO) {
        if (shopDO == null) {
            return null;
        }
        
        return MallShopVO.builder()
                .id(shopDO.getId())
                .tenantId(shopDO.getTenantId())
                .shopName(shopDO.getShopName())
                .shopStatus(shopDO.getShopStatus())
                .shopIcon(shopDO.getShopIcon())
                .contactPhone(shopDO.getContactPhone())
                .build();
    }

    /**
     * 批量创建群聊成员DO对象列表
     *
     * @param memberIds 成员ID集合（可为null或空）
     * @param conversationId 会话ID
     * @return 群聊成员DO对象列表
     */
    public static List<ChatConversationMemberDO> toGroupMembers(Collection<String> memberIds, String conversationId,String memberType) {
        List<ChatConversationMemberDO> members = new ArrayList<>();
        
        if (memberIds == null || memberIds.isEmpty()) {
            return members;
        }

        Date joinedAt = new Date();
        for (String memberId : memberIds) {
            if (memberId != null && !memberId.isEmpty()) {
                ChatConversationMemberDO member = new ChatConversationMemberDO();
                member.setConversationId(conversationId);
                // 不区分客户和客服，统一设置为member类型
                member.setMemberType(memberType);
                member.setMemberId(memberId);
                member.setJoinedAt(joinedAt);
                members.add(member);
            }
        }

        return members;
    }
}


