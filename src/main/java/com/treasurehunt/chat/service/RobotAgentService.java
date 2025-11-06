package com.treasurehunt.chat.service;

import com.treasurehunt.chat.domain.ChatMessageDO;
import com.treasurehunt.chat.mapper.ChatMessageMapper;
import com.treasurehunt.chat.component.manager.MessageIdManager;
import com.treasurehunt.chat.component.manager.MessageIdGenerateResult;
import com.treasurehunt.chat.framework.core.websocket.distributed.delivery.NotificationDispatcher;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Set;

/**
 * 机器人客服服务
 * 根据工作时间和客服在线状态发送不同的提示信息
 */
@Slf4j
@Service
public class RobotAgentService {

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private MessageIdManager messageIdManager;

    @Autowired
    private NotificationDispatcher notificationDispatcher;

    @Autowired(required = false)
    private StatefulRedisConnection<String, String> redisConnection;

    // 工作时间配置（从Nacos读取）
    @Value("${chat.working-hours.start:09:00}")
    private String workingHoursStart;

    @Value("${chat.working-hours.end:18:00}")
    private String workingHoursEnd;

    /**
     * 自动回复时间窗口（小时）
     * 在指定时间窗口内，同一个 conversation_id 只回复一次
     */
    @Value("${chat.robot-auto-reply.window-hours:4}")
    private int autoReplyWindowHours = 4;

    /**
     * Redis key 前缀：记录每个会话的最后自动回复时间
     */
    private static final String REDIS_KEY_PREFIX = "chat:robot:auto-reply:";

    /**
     * 判断当前是否在工作时间内
     */
    private boolean isWorkingHours() {
        try {
            // 使用系统默认时区，与主应用保持一致
            ZoneId zoneId = ZoneId.systemDefault();
            LocalTime now = LocalTime.now(Clock.system(zoneId));
            LocalTime start = LocalTime.parse(workingHoursStart, DateTimeFormatter.ofPattern("HH:mm"));
            LocalTime end = LocalTime.parse(workingHoursEnd, DateTimeFormatter.ofPattern("HH:mm"));

            // 修复：包含边界时间（09:00和18:00也应该算工作时间）
            // 使用 !now.isBefore(start) 等同于 now >= start
            // 使用 !now.isAfter(end) 等同于 now <= end
            boolean inWorkingHours = !now.isBefore(start) && !now.isAfter(end);
            
            log.debug("工作时间判断: now={}, start={}, end={}, zoneId={}, result={}", 
                    now, start, end, zoneId, inWorkingHours);
            
            return inWorkingHours;
        } catch (Exception e) {
            log.error("解析工作时间配置失败: start={}, end={}", 
                    workingHoursStart, workingHoursEnd, e);
            return false; // 解析失败时默认非工作时间
        }
    }

    /**
     * 发送无客服在线时的提示消息
     * 根据工作时间决定回复内容
     * 对于同一个 conversation_id，在指定的时间窗口内（默认2小时）只回复一次，其他时间保持静默
     */
    public void sendAutoReplyMessage(String conversationId, Long customerServerMsgId, String customerId,Long shopId) {
        try {
            // 检查是否在时间窗口内已经回复过
            if (hasRepliedRecently(conversationId)) {
                log.debug("机器人客服在时间窗口内已回复过，保持静默: conversationId={}, windowHours={}", 
                        conversationId, autoReplyWindowHours);
                return;
            }

            boolean isWorkingTime = isWorkingHours();
            String messageContent;
            String messageType;

            if (isWorkingTime) {
                messageContent = "您好，客服正在忙碌中，请稍等片刻，我们会尽快为您服务！";
                messageType = "客服忙碌";
            } else {
                messageContent = "您好，当前不是客服工作时间，但您发的消息我们已经记录，上班后及时反馈给您，感谢您的理解！";
                messageType = "非工作时间";
            }

            log.info("机器人客服发送{}提示: conversationId={}, customerServerMsgId={}, isWorkingTime={}",
                    messageType, conversationId, customerServerMsgId, isWorkingTime);

            sendRobotMessage(conversationId, customerServerMsgId, messageContent, messageType, customerId,shopId);

            // 记录本次回复时间，设置过期时间为窗口时长
            recordAutoReplyTime(conversationId);

        } catch (Exception e) {
            log.error("机器人客服发送无客服在线提示失败: conversationId={}", conversationId, e);
        }
    }

    /**
     * 检查是否在时间窗口内已经回复过
     * @param conversationId 会话ID
     * @return true 表示在时间窗口内已回复过，需要保持静默；false 表示可以回复
     */
    private boolean hasRepliedRecently(String conversationId) {
        if (redisConnection == null) {
            log.warn("Redis连接不可用，跳过时间窗口检查，允许回复: conversationId={}", conversationId);
            return false; // Redis不可用时，允许回复（降级策略）
        }

        try {
            RedisCommands<String, String> commands = redisConnection.sync();
            String cacheKey = REDIS_KEY_PREFIX + conversationId;
            String lastReplyTime = commands.get(cacheKey);
            
            if (lastReplyTime != null) {
                log.debug("检测到会话在时间窗口内已回复过: conversationId={}, lastReplyTime={}", 
                        conversationId, lastReplyTime);
                return true;
            }
            
            return false;
        } catch (Exception e) {
            log.error("检查自动回复时间窗口失败，降级为允许回复: conversationId={}", conversationId, e);
            return false; // 异常时降级为允许回复
        }
    }

    /**
     * 记录自动回复时间，设置过期时间为窗口时长
     * @param conversationId 会话ID
     */
    private void recordAutoReplyTime(String conversationId) {
        if (redisConnection == null) {
            log.warn("Redis连接不可用，无法记录自动回复时间: conversationId={}", conversationId);
            return;
        }

        try {
            RedisCommands<String, String> commands = redisConnection.sync();
            String cacheKey = REDIS_KEY_PREFIX + conversationId;
            String currentTime = String.valueOf(System.currentTimeMillis());
            
            // 使用 SETEX 设置值和过期时间（秒）
            int expireSeconds = autoReplyWindowHours * 3600;
            commands.setex(cacheKey, expireSeconds, currentTime);
            
            log.debug("记录自动回复时间: conversationId={}, expireSeconds={}", conversationId, expireSeconds);
        } catch (Exception e) {
            log.error("记录自动回复时间失败: conversationId={}", conversationId, e);
            // 记录失败不影响主流程，只记录日志
        }
    }


    /**
     * 发送机器人消息的通用方法
     */
    private void sendRobotMessage(String conversationId, Long customerServerMsgId, String content, String messageType,String customerId,Long shopId) {
        try {
            // 使用MessageIdManager生成serverMsgId，确保唯一性
            MessageIdGenerateResult genResult = messageIdManager.generateServerMsgId(conversationId);
            Long robotServerMsgId = genResult.getServerMsgId();

            // 构造机器人消息
            ChatMessageDO robotMessage = ChatMessageDO.builder()
                    .conversationId(conversationId)
                    .serverMsgId(robotServerMsgId)
                    .clientMsgId("robot_system_" + System.currentTimeMillis()) // 机器人消息ID
                    .senderId("robot_001") // 机器人ID
                    .fromUserId("robot_001")
                    .msgType("text")
                    .content(content)
                    .payloadJson("{\"system_message\": true, \"message_type\": \"" + messageType + "\"}")
                    .status("PENDING")
                    .pushAttempts(0)
                    .shopId(shopId)
                    .createdAt(new Date())
                    .build();

            // 插入数据库，根据Redis可用性选择插入方式
            int insertResult;
            if (genResult.isRedisAvailable()) {
                insertResult = chatMessageMapper.insertOne(robotMessage);
            } else {
                // 降级模式：使用ON DUPLICATE KEY自增
                insertResult = chatMessageMapper.insertOneOnDupIncrAnno(robotMessage);
            }
            
            if (insertResult > 0) {
                log.info("机器人消息插入成功: conversationId={}, robotServerMsgId={}, messageType={}, redisAvailable={}", 
                        conversationId, robotServerMsgId, messageType, genResult.isRedisAvailable());
                // 推送给客户
                notificationDispatcher.dispatch(conversationId, robotServerMsgId, "robot_001", Set.of(customerId));
                log.info("机器人消息推送给客户: conversationId={}, robotServerMsgId={}, messageType={}", 
                        conversationId, robotServerMsgId, messageType);
            } else {
                log.error("机器人消息插入失败: conversationId={}", conversationId);
            }
        } catch (Exception e) {
            log.error("机器人客服发送消息失败: conversationId={}, messageType={}", conversationId, messageType, e);
        }
    }
}
