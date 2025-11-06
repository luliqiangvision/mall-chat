package com.treasurehunt.chat.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.util.Date;

/**
 * 聊天消息实体类
 * 支持新的 envelope 结构和消息状态管理
 */
@Schema(description = "聊天消息")
public class ChatMessage implements Serializable {
    
    @Schema(description = "客户端消息ID，用于幂等和重发去重")
    private String clientMsgId;
    
    @Schema(description = "服务端消息ID，会话内递增序号")
    private Long serverMsgId;
    
    @Schema(description = "会话ID")
    private String conversationId;
    
    @Schema(description = "消息类型")
    private String type;
    
    @Schema(description = "消息内容")
    private String content;
    
    @Schema(description = "发送者用户ID")
    private String fromUserId;
    
    @Schema(description = "发送者ID，如果是转发，那么这个字段就是转发人的id")
    private String senderId;
    
    @Schema(description = "发送时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date timestamp;
    
    @Schema(description = "发送者用户名")
    private String fromUsername;
    
    @Schema(description = "消息状态：PENDING,PUSHED,DELIVERED,READ,FAILED")
    private String status;
    
    @Schema(description = "UI状态：sending,sent,failed")
    private String uiState;
    
    @Schema(description = "消息载荷，用于扩展消息内容")
    private Object payload;

    @Schema(description = "店铺ID")
    private Long shopId;
    
    private static final long serialVersionUID = 1L;
    
    public ChatMessage() {
        this.timestamp = new Date();
        this.status = "PENDING"; // 默认待处理
        this.uiState = "sending"; // 默认发送中
    }
    
    public ChatMessage(String type, String content) {
        this();
        this.type = type;
        this.content = content;
    }
    
    public ChatMessage(String type, String fromUserId, String content) {
        this();
        this.type = type;
        this.fromUserId = fromUserId;
        this.content = content;
    }
    
    public ChatMessage(String clientMsgId, String conversationId, String type, String fromUserId, String content) {
        this();
        this.clientMsgId = clientMsgId;
        this.conversationId = conversationId;
        this.type = type;
        this.fromUserId = fromUserId;
        this.content = content;
    }
    
    // Getters and Setters
    public String getClientMsgId() {
        return clientMsgId;
    }
    
    public void setClientMsgId(String clientMsgId) {
        this.clientMsgId = clientMsgId;
    }
    
    public Long getServerMsgId() {
        return serverMsgId;
    }
    
    public void setServerMsgId(Long serverMsgId) {
        this.serverMsgId = serverMsgId;
    }
    
    public String getConversationId() {
        return conversationId;
    }
    
    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public String getFromUserId() {
        return fromUserId;
    }
    
    public void setFromUserId(String fromUserId) {
        this.fromUserId = fromUserId;
    }
    
    public String getSenderId() {
        return senderId;
    }
    
    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }
    
    public Date getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getFromUsername() {
        return fromUsername;
    }
    
    public void setFromUsername(String fromUsername) {
        this.fromUsername = fromUsername;
    }
    
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getUiState() {
        return uiState;
    }
    
    public void setUiState(String uiState) {
        this.uiState = uiState;
    }
    
    public Object getPayload() {
        return payload;
    }
    
    public void setPayload(Object payload) {
        this.payload = payload;
    }

    public Long getShopId() {
        return shopId;
    }

    public void setShopId(Long shopId) {
        this.shopId = shopId;
    }
    
    @Override
    public String toString() {
        return "ChatMessage{" +
                "clientMsgId='" + clientMsgId + '\'' +
                ", serverMsgId=" + serverMsgId +
                ", conversationId='" + conversationId + '\'' +
                ", type='" + type + '\'' +
                ", content='" + content + '\'' +
                ", fromUserId='" + fromUserId + '\'' +
                ", senderId='" + senderId + '\'' +
                ", timestamp=" + timestamp +
                ", fromUsername='" + fromUsername + '\'' +
                ", status='" + status + '\'' +
                ", uiState='" + uiState + '\'' +
                ", payload=" + payload +
                ", shopId=" + shopId +
                '}';
    }
}
