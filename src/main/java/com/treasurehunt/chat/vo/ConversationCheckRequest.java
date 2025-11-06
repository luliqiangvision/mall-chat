package com.treasurehunt.chat.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 会话预检请求
 */
@Data
@Schema(description = "会话预检请求")
public class ConversationCheckRequest implements Serializable {
    
    @Schema(description = "用户ID", required = true)
    private String userId;
    
    @Schema(description = "店铺ID", required = true)
    private Long shopId;
    
    private static final long serialVersionUID = 1L;
}
