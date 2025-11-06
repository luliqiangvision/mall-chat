package com.treasurehunt.chat.framework.core.websocket.distributed.delivery;

/**
 * 告警服务接口
 * 
 * 框架层只定义接口，具体实现由业务层提供
 * 
 * @author gaga
 * @since 2025-10-06
 */
public interface AlertService {

    /**
     * 发送消息推送失败告警
     * @param userId 用户ID
     * @param conversationId 会话ID
     * @param serverMsgId 消息ID
     * @param retryCount 重试次数
     * @param errorMessage 错误信息
     */
    void sendPushFailureAlert(String userId, String conversationId, long serverMsgId, 
                             int retryCount, String errorMessage);

    /**
     * 发送系统异常告警
     * @param module 模块名称
     * @param method 方法名称
     * @param errorMessage 错误信息
     */
    void sendSystemErrorAlert(String module, String method, String errorMessage);
}
