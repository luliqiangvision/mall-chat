package com.treasurehunt.chat.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 会话预检响应
 */
@Data
@Schema(description = "会话预检响应")
public class ConversationCheckResponse implements Serializable {
    
    @Schema(description = "是否有会话")
    private Boolean hasConversation;
    
    @Schema(description = "会话ID")
    private String conversationId;
    
    @Schema(description = "客户端最大服务端消息ID")
    private Long clientMaxServerMsgId;
    
    @Schema(description = "店铺信息")
    private MallShopVO shop;
    
    private static final long serialVersionUID = 1L;
}
