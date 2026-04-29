package com.treasurehunt.chat.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 客服信息表
 * 管理客服的基本信息、状态和负载情况
 */
@Data
@TableName("chat_agent")
public class ChatAgentDO {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 客服ID（唯一标识） */
    @TableField("agent_id")
    private String agentId;

    /** 客服工号（可空，预留字段） */
    @TableField("agent_no")
    private String agentNo;

    /** 统一内部主体ID（IAM subject_id） */
    @TableField("subject_id")
    private Long subjectId;

    /** 客服姓名 */
    @TableField("agent_name")
    private String agentName;

    /** 密码（BCrypt加密） */
    @TableField("password")
    private String password;

    /** 
     * 客服类型：
     * - pre-sales: 售前客服
     * - after-sales: 售后客服  
     * - system: 系统客服
     */
    @TableField("agent_type")
    private String agentType;

    /** 
     * 客服状态：
     * - active: 活跃
     * - inactive: 非活跃
     * - offline: 离线
     */
    @TableField("status")
    private String status;

    /** 最大并发会话数 */
    @TableField("max_concurrent_conversations")
    private Integer maxConcurrentConversations;

    /** 当前会话数 */
    @TableField("current_conversations")
    private Integer currentConversations;

    /** 多租户标识，亦即商户/店铺ID */
    @TableField("tenant_id")
    private Long tenantId;

    /** 业务线标识（由网关透传 X-Business-Line） */
    @TableField("business_line")
    private String businessLine;

    /** 创建时间 */
    @TableField("created_at")
    private Date createdAt;

    /** 更新时间 */
    @TableField("updated_at")
    private Date updatedAt;
}
