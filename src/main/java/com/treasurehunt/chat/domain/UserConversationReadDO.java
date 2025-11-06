package com.treasurehunt.chat.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("user_conversation_read")
public class UserConversationReadDO {

    /** 逻辑主键ID（不参与查询） */
    @TableId
    @TableField("id")
    private Long id;

    /** 会话ID */
    @TableField("conversation_id")
    private String conversationId;

    /** 客户用户ID（纯数字类型，性能更好） */
    @TableField("user_id")
    private Long userId;

    /** 客服ID（纯数字类型，性能更好） */
    @TableField("agent_id")
    private Long agentId;

    /** 已读指针（会话内最大已读 server_msg_id） */
    @TableField("last_read_server_msg_id")
    private Long lastReadServerMsgId;

    /** 更新时间 */
    @TableField("updated_at")
    private Date updatedAt;
}


