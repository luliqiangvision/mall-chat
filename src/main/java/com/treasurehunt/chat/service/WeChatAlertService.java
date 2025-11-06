package com.treasurehunt.chat.service;

import com.treasurehunt.chat.framework.core.websocket.distributed.delivery.AlertService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 企业微信告警服务实现（业务层）
 * 
 * 这是业务层的具体实现，框架层只提供接口约束
 * 
 * @author gaga
 * @since 2025-10-06
 */
@Slf4j
@Service
public class WeChatAlertService implements AlertService {

    @Override
    public void sendPushFailureAlert(String userId, String conversationId, long serverMsgId, 
                                     int retryCount, String errorMessage) {
        // TODO: 实现企业微信告警逻辑
        log.warn("Push failure alert: userId={}, conversationId={}, serverMsgId={}, retryCount={}, error={}", 
            userId, conversationId, serverMsgId, retryCount, errorMessage);
    }

    @Override
    public void sendSystemErrorAlert(String module, String method, String errorMessage) {
        // TODO: 实现企业微信告警逻辑
        log.warn("System error alert: module={}, method={}, error={}", 
            module, method, errorMessage);
    }
}
