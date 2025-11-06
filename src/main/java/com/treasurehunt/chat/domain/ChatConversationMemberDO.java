package com.treasurehunt.chat.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("chat_conversation_member")
public class ChatConversationMemberDO {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话ID（与 chat_conversation.conversation_id 关联） */
    @TableField("conversation_id")
    private String conversationId;

    /** 成员类型：customer|agent|system */
    @TableField("member_type")
    private String memberType;

    /** 成员ID（用户ID或系统标识） */
    @TableField("member_id")
    private String memberId;

    /** 加入时间 */
    @TableField("joined_at")
    private Date joinedAt;

    /** 离开时间 */
    @TableField("left_at")
    private Date leftAt;
}


