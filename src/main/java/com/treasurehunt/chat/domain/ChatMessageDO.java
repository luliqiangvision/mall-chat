package com.treasurehunt.chat.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
@TableName("chat_message")
public class ChatMessageDO {

    /** 自增主键（占位，业务以 conversation_id 分库分表，按该键查询） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话ID（分库分表/查询主键） */
    @TableField("conversation_id")
    private String conversationId;

    /** 会话内递增序号 */
    @TableField("server_msg_id")
    private Long serverMsgId;

    /** 客户端消息ID（幂等） */
    @TableField("client_msg_id")
    private String clientMsgId;

    /** 发送者ID,如果是转发,那么这个字段就是转发人的id */
    @TableField("sender_id")
    private String senderId;

    /** 来源用户ID */
    @TableField("from_user_id")
    private String fromUserId;

    /** 消息类型 */
    @TableField("msg_type")
    private String msgType;

    /** 文本内容（纯文本消息的正文） */
    @TableField("content")
    private String content;

    /** 业务载荷JSON */
    @TableField("payload_json")
    private String payloadJson;

    /** 媒体哈希（图片/视频等，原始SHA-256 32字节） */
    @TableField("hash_code")
    private byte[] hashCode;

    /** 消息状态：PENDING,PUSHED,DELIVERED,READ,FAILED */
    @TableField("status")
    private String status;

    /** 推送重试次数 */
    @TableField("push_attempts")
    private Integer pushAttempts;

    /** 店铺ID */
    @TableField("shop_id")
    private Long shopId;

    /** 创建时间 */
    @TableField("created_at")
    private Date createdAt;

    /** 送达时间 */
    @TableField("delivered_at")
    private Date deliveredAt;
}


