package com.treasurehunt.chat.framework.core.websocket.distributed.delivery;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 群聊广播器
 * <p>
 * 职责：
 * - 处理群聊消息的广播推送
 * - 使用 Redis Stream 进行全局广播
 * - 各实例接收后自己过滤处理
 *
 * @author gaga
 * @since 2025-10-06
 */
@Slf4j
@Component
public class GroupChatBroadcaster {

    @Autowired
    private ChatStreamClient chatStreamClient;
    @Autowired
    private ObjectMapper objectMapper;

    // 配置参数
    @Value("${spring.application.name}")
    private String applicationName;

    /**
     * Redis Stream 最大消息数量（长度裁剪）
     * <p>
     * 消息大小估算（基于 NotificationMessage 结构）：
     * - 文本消息：~0.6-0.7KB/entry
     * - 图片消息：~0.7KB/entry
     * - 视频消息：~1.2KB/entry
     * <p>
     * 当前配置 100000 条消息的内存占用：
     * - 按文本消息计算：~60 -70MB
     * - 按图片消息计算：~70MB
     * - 按视频消息计算：~120MB
     * <p>
     * 注意：Redis Stream 使用长度裁剪（MAXLEN），没有逐条TTL
     * 建议根据业务消息量和内存预算调整此值
     */
    @Value("${chat.stream.max-length:100000}")
    private long maxLength;

    /**
     * 初始化时打印内存使用报告
     */
    @PostConstruct
    public void init() {
        log.info("GroupChatBroadcaster initialized with maxLength={}", maxLength);

    }

    /**
     * 广播群聊消息
     *
     * @param conversationId 会话ID
     * @param serverMsgId    消息ID
     * @param senderId       发送者ID
     * @param memberIds      目标用户列表
     */
    public void broadcast(String conversationId, long serverMsgId, String senderId, Set<String> memberIds) {
        try {
            // 1. 创建广播消息
            NotificationMessage message = new NotificationMessage(
                    applicationName, // 使用Spring应用名作为服务类型
                    conversationId,
                    serverMsgId,
                    senderId, // 发送者
                    memberIds // 目标用户列表
            );

            // 2. 广播到 Redis Stream，使用 Lettuce 原生 API 的 XADD MAXLEN ~ 命令
            // 将消息对象序列化为 JSON 字符串
            String messageJson = objectMapper.writeValueAsString(message);
            Map<String, String> body = new HashMap<>();
            body.put("message", messageJson);
            
            // 使用 Lettuce 原生 API 写入 Stream，自动进行 MAXLEN ~ 近似裁剪
            chatStreamClient.addBroadcast(body);

            log.debug(
                    "Group chat message broadcasted: conversationId={}, serverMsgId={}, senderId={}, targetCount={}, maxLength={}",
                    conversationId, serverMsgId, senderId, memberIds.size(), maxLength);

        } catch (Exception e) {
            log.error("Failed to broadcast group chat message: conversationId={}, serverMsgId={}, senderId={}",
                    conversationId, serverMsgId, senderId, e);
        }
    }
}
