package com.treasurehunt.chat.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;
import java.util.Set;

@Data
@TableName("chat_conversation")
public class ChatConversationDO {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话ID（外部可用uuid，业务方识别用） */
    @TableField("conversation_id")
    private String conversationId;

    /** 客户用户ID（发起方） */
    @TableField("customer_id")
    private String customerId;

    /** 分配的客服座席ID（可为空，未分配时为null） */
    @TableField("agent_id")
    private Set<String> agentIds;

    /** 
     * 会话状态：
     * - waiting: 等待客服响应(群聊里还没有人类客服)
     * - active: 正常活跃会话(还在聊天)
     * - closed: 会话关闭(客服询问客户是否还有问题,客服手动关闭)
     * - deleted_by_customer: 客户删除会话(软删除)
     * - deleted_by_agent: 客服删除会话(预留,主要是有人来骚扰,拉黑用的)
     */
    @TableField("status")
    private String status;

    /** 多租户标识，亦即商户/店铺ID */
    @TableField("tenant_id")
    private Long tenantId;

    /** 店铺ID（可为空） */
    @TableField("shop_id")
    private Long shopId;

    /** 创建时间 */
    @TableField("created_at")
    private Date createdAt;

    /** 更新时间 */
    @TableField("updated_at")
    private Date updatedAt;
}


