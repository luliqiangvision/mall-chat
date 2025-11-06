package com.treasurehunt.chat.enums;

/**
 * 会话状态枚举
 * 
 * 业务场景和状态流转：
 * 
 * 场景1：正常流程
 * 客户  新的  聊天窗口发消息 → waiting → 客服加入 → active → 客服根据问题是否已经解决或者无法解决,征求客户是否还有问题需要询问,如果没有问题就关闭会话 → closed
 *                                                        → 客户是来骚扰的 → deleted_by_agent
 *                                                        → 客户自己删除聊天窗口走了 → deleted_by_customer
 *                                                        → 问题还没解决,就一直active下去,注意：客服严禁未解决客户问题的情况下全部离开群聊（后期要实现这个限制）
 * 场景2：客户重新咨询
 * closed → 客户重新发消息 → 分两种情况：
 * ├─ 群聊里还有人类客服 → active（直接推送信息）
 * └─ 群聊里没有人类客服 → waiting（重新开放抢单）
 * 
 * 
 * @author gaga
 * @since 2024-01-01
 */
public enum ConversationStatusEnum {
    
    /**
     * 等待客服响应
     * 群聊里还没有人类客服
     */
    WAITING("waiting", "等待客服响应"),
    
    /**
     * 正常活跃会话
     * 还在聊天
     */
    ACTIVE("active", "正常活跃会话"),
    
    /**
     * 会话关闭
     * 客服询问客户是否还有问题，客服手动关闭
     */
    CLOSED("closed", "会话关闭"),
    
    /**
     * 客户删除会话
     * 软删除
     */
    DELETED_BY_CUSTOMER("deleted_by_customer", "客户删除会话"),
    
    /**
     * 客服删除会话
     * 预留，主要是有人来骚扰，拉黑用的
     */
    DELETED_BY_AGENT("deleted_by_agent", "客服删除会话");
    
    private final String code;
    private final String description;
    
    ConversationStatusEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * 根据代码获取枚举
     */
    public static ConversationStatusEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ConversationStatusEnum status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return null;
    }
    
    /**
     * 判断是否为活跃状态（可以接收消息）
     */
    public boolean isActive() {
        return this == ACTIVE;
    }
    
    /**
     * 判断是否为等待状态（可以抢单）
     */
    public boolean isWaiting() {
        return this == WAITING;
    }
    
    /**
     * 判断是否为可激活状态（waiting或active）
     */
    public boolean canActivate() {
        return this == WAITING || this == ACTIVE;
    }
    
    /**
     * 判断是否为最终状态（已删除）
     */
    public boolean isDeleted() {
        return this == DELETED_BY_CUSTOMER || this == DELETED_BY_AGENT;
    }
    
    /**
     * 判断是否为关闭状态
     */
    public boolean isClosed() {
        return this == CLOSED;
    }
    
    // TODO: 后期使用状态机来实现状态转换逻辑
    // 包括 canTransitionTo() 和 getTransitionDescription() 方法
    // 状态机可以更好地管理复杂的状态转换规则和业务逻辑
}
