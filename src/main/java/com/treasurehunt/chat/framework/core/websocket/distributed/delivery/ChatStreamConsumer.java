package com.treasurehunt.chat.framework.core.websocket.distributed.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.api.async.RedisAsyncCommands;
import jakarta.annotation.PreDestroy;
import org.springframework.core.task.TaskExecutor;
import com.treasurehunt.chat.framework.core.websocket.distributed.spi.InstanceRegistry;

import io.lettuce.core.Consumer;
import io.lettuce.core.RedisClient;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.XGroupCreateArgs;
import io.lettuce.core.XReadArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 群聊消息消费者(广播模式),当前模式最大的问题是缩容和扩容的时候,消费者id会飘移,导致消息一直无法消费,后期考虑使用配置的方式,直接在nacos上配置消费者的id列表,消费实例来均分这些id,这样哪怕缩容也不怕
 * <p>
 * 职责：
 * - 消费 Redis Stream 中的群聊消息
 * - 过滤掉发送者，只推送给本机在线的目标用户
 * - 处理消息确认和重试
 * <p>
 * 架构设计：
 * 采用方案2（独立Stream）实现真正的服务隔离,只成立在“语义/消费层面”，不是资源/故障层面的完全隔离,也就是如果跟其他服务共享redis集群,彼此之间资源上还是有竞争的：
 * <p>
 * 1. 真正的隔离
 * 聊天服务只消费 chat:global
 * 订单服务只消费 order:global
 * 用户服务只消费 user:global
 * 互不干扰
 * <p>
 * 2. 防止干扰
 * 消息污染：其他服务的消息不会进入聊天服务的Stream
 * 性能隔离：聊天服务的高频消息不会影响其他服务
 * 故障隔离：某个服务的Stream问题不会影响其他服务
 * <p>
 * 3. 扩展性
 * 新服务：可以轻松添加新的独立Stream
 * 服务拆分：服务拆分时不会影响现有Stream
 * 资源控制：每个服务可以独立控制Stream的配置
 * <p>
 * 架构图：
 * <p>
 * ┌─────────────────┐     ┌─────────────────┐    ┌─────────────────┐
 * │   聊天服务       │     │   订单服务      │     │     用户服务     │
 * │ 消费: chat:global│    │消费: order:global│    │消费: user:global│
 * └─────────────────┘     └─────────────────┘    └─────────────────┘
 * │                       │                       │
 * └───────────────────────┼───────────────────────┘
 * │
 * ┌─────────────────┐
 * │   Redis Stream  │
 * │ chat:global     │
 * │ order:global    │
 * │ user:global     │
 * └─────────────────┘
 * <p>
 * 消费模式：
 * -
 * 广播模式：每个实例使用独立的 group（=instanceId）实现“广播”。如果是当前实例需要处理的用户，则处理成功后 ACK；若本实例无目标用户，直接 ACK。
 * Redis Stream 不支持逐条 TTL，不做消息 TTL；保留策略依赖 XADD MAXLEN 近似裁剪或按 MINID 裁剪（详见 application.yaml 的 chat.stream.max-length）。
 * - 阻塞消费：类似 RocketMQ，有消息就处理，没消息就阻塞等待
 * - 独立确认：每个实例独立确认消息处理
 * - 消费位置：类似 RocketMQ 的 CONSUME_FROM_FIRST_UNCONSUMED，从第一个未消费的消息开始读取
 *
 * @author gaga
 * @since 2025-10-06
 */
@Slf4j
@Component
public class ChatStreamConsumer {
    @Autowired
    private RedisClient redisClient;

    @Autowired
    private UserSessionMetadataManager userSessionMetadataManager;

    @Autowired
    private NotifyPushSender notifyPushSender;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AlertService alertService;

    @Autowired
    @Qualifier("streamConsumerExecutor")
    private TaskExecutor taskExecutor;

    // ===================== 配置项 =====================

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${server.port}")
    private String serverPort;

    /**
     * 显式配置的实例IP地址
     * 优先级最高，用于防止容器环境IP干扰
     * 类似 Dubbo 的配置方式
     */
    @Value("${websocket.instance.ip}")
    private String configuredInstanceIp;

    @Value("${chat.stream.max-length:10000}")
    private long maxLength;

    // ===================== 运行时字段 =====================
    private String instanceId;

    // Lettuce 连接与命令
    private StatefulRedisConnection<String, String> connection;


    // Stream 常量
    private static final String STREAM_KEY = "chat:global";

    // 实例注册信息（用于获取稳定的实例ID），通过SPI接口解耦实现
    @Autowired
    private InstanceRegistry nacosInstanceRegistry;

    // 槽位管理（用于分配稳定的消费者组/消费者名）
    @Autowired
    private SlotManager slotManager;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private RedisAsyncCommands<String, String> async;
    private final ExecutorService workers = Executors.newFixedThreadPool(4);
    // ===================== 生命周期 =====================

    // 初始化资源改为在应用就绪后执行，避免与 Nacos 实例ID获取的时序冲突
    public void init() {
        // 建立 Lettuce 连接
        this.connection = redisClient.connect();
        this.async = connection.async();
        // 优先使用槽位管理提供的稳定组名（slot-<k>），失败则回退到 Nacos 或本机 IP:port
        Integer slotId = null;
        try {
            // 槽位分配入参硬编码，业务无感
            slotId = slotManager.acquireSlot("chatinstanceslotkey");
        } catch (Exception e) {
            log.warn("Slot allocation failed, will fallback to non-slot consumer id", e);
        }

        if (slotId != null) {
            this.instanceId = applicationName + "-slot-" + slotId;
        } else {
            // 从注册中心获取只剩下惟一标识号的意义了,原本以为nacos里的instanceId是数字,可以用于防止容器启动导致ip变动,影响消费进度的记录和继续消费,但发现它也是跟着ip走的,因此只能用来作为redis连不上时候的降级处理,没有原来的作用了
            String nacosId = nacosInstanceRegistry != null ? nacosInstanceRegistry.getCurrentInstanceId() : null;
            if (nacosId == null) {
                // 二级降级：使用当前机器 IP（容器场景下可能未显式配置 configuredInstanceIp）
                String ip;
                try {
                    ip = java.net.InetAddress.getLocalHost().getHostAddress();
                } catch (Exception ex) {
                    ip = configuredInstanceIp; // 最后兜底回配置
                }
                nacosId = ip + ":" + serverPort;
                log.error("Nacos instanceId not ready, fallback to {}", nacosId);
            }
            this.instanceId = applicationName + "-" + nacosId;
        }
        log.info("ChatStreamConsumer initialized for broadcast mode: instanceId={}", instanceId);
        // 每个实例独立的 group（= instanceId），实现“广播”（同一消息被每个组各自消费一次）
        try {
            async.xgroupCreate(
                    XReadArgs.StreamOffset.from(STREAM_KEY, "$"),// 从最后一个没有提交ack的地方读取
                    instanceId,// 充当消费者组标识,也就是消费者组名称
                    XGroupCreateArgs.Builder.mkstream(true));// 当chat:global 还不存在，Redis 也会帮你创建一个空的 Stream，并成功创建 group。这让消费者启动时不依赖生产者顺序，更稳健。
            log.info("Created stream group for broadcast: stream={}, group={}", STREAM_KEY, instanceId);
        } catch (Exception e) {
            // BUSYGROUP 表示已存在
            String msg = e.getMessage();
            if (msg != null && msg.contains("BUSYGROUP")) {
                log.info("Stream group already exists: stream={}, group={}", STREAM_KEY, instanceId);
            } else {
                throw e;
            }
        }
    }


    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        // 在应用完全就绪后再初始化资源与创建消费组，确保能够拿到稳定的实例ID
        init();
        startConsumingLoop();
    }

    // 当槽位租约丢失时，重新初始化并尝试重新分配槽位
    @EventListener(SlotLeaseLostEvent.class)
    public void onSlotLost() {
        try {
            log.warn("Slot lease lost, reinitializing consumer with new slot...");
            shutdown();
        } catch (Exception ignore) {}
        onReady();
    }
    @SuppressWarnings("unchecked")
    private void startConsumingLoop() {
        log.info("Starting Redis Stream consumer for group chat messages...");
        if (!running.get()) return;
        // 广播模式：每个实例独立消费，不需要消费者组
        // 使用实例ID作为消费者名称，从第一个未消费的消息开始读取（类似 RocketMQ 的 CONSUME_FROM_FIRST_UNCONSUMED）
        // 使用异步方式读取，借助lettuce的netty的事件驱动，
        async.xreadgroup(
                Consumer.from(instanceId, instanceId),
                XReadArgs.Builder.block(Duration.ZERO).count(100),
                XReadArgs.StreamOffset.lastConsumed(STREAM_KEY)// 等价于 ReadOffset.lastConsumed()
        ).whenComplete((msgs, ex) -> {
            if (!running.get()) return;
            if (ex != null) {
                log.error("xreadgroup error, retry in 3s", ex);
                CompletableFuture.delayedExecutor(3, TimeUnit.SECONDS).execute(this::startConsumingLoop);
                return;
            }

            if (msgs != null && !msgs.isEmpty()) {
                for (StreamMessage<String, String> m : msgs) {
                    workers.execute(() -> {
                        try {
                            log.debug("消费端接收到消息,准备消费消息: {}", m.getId());
                            boolean shouldAck = processMessage(m);
                            if (shouldAck) {
                                // 确认消息（广播模式下每个实例独立确认）
                                async.xack(STREAM_KEY, instanceId, m.getId());
                                log.debug("Processed and acknowledged message: {}", m.getId());
                            } else {
                                // 没有成功不提交ack
                                log.debug("Message not ACKed, will retry: {}", m.getId());
                            }
                        } catch (Throwable t) {
                            log.error("process fail {}", m.getId(), t);
                            // 处理失败的不 ACK，会重试
                        }
                    });
                }
            }
            // 继续下一轮异步读取（无 while；由 Netty 事件驱动）
            startConsumingLoop();
        });
    }


    // 停机
    public void shutdown() {
        log.info("关闭消费资源");
        running.set(false);
        connection.close();        // 让挂起的命令快速失败退出
        workers.shutdown();
    }

    @PreDestroy
    public void onDestroy() {
        shutdown();
    }

    /**
     * 处理单条消息
     *
     * @return true 如果应该ACK，false 如果不应该ACK
     */
    private boolean processMessage(StreamMessage<String, String> record) {
        try {
            // 解析消息
            NotificationMessage message = parseMessage(record);

            // 只处理本服务的消息
            if (!applicationName.equals(message.getServiceType())) {
                return true; // 不是本服务消息，直接ACK
            }

            // 1. 排除发送者
            Set<String> targetUserIds = message.getTargetUserIds();
            if (targetUserIds != null) {
                targetUserIds.remove(message.getSenderId());
            }

            // 2. 检查是否有目标用户在本实例并推送
            boolean hasLocalUsers = false;
            boolean allPushSuccess = true;

            if (targetUserIds != null) {
                for (String userId : targetUserIds) {
                    Set<String> sessionIds = userSessionMetadataManager.getSessionIdsByUserId(userId);
                    if (sessionIds != null && !sessionIds.isEmpty()) {
                        hasLocalUsers = true;
                        // 推送给客户端
                        for (String sessionId : sessionIds) {
                            try {
                                notifyPushSender.sendNotifyPullLocal(
                                        sessionId,
                                        message.getConversationId(),
                                        message.getServerMsgId());
                                log.debug("Pushed message to local user: userId={}, sessionId={}, conversationId={}",
                                        userId, sessionId, message.getConversationId());
                            } catch (Exception e) {
                                log.error("Failed to push message to user: userId={}, sessionId={}", userId, sessionId,
                                        e);
                                allPushSuccess = false;
                            }
                        }
                    }
                }
            }

            // 3. ACK策略：
            // - 如果没有目标用户在本实例，直接ACK（不需要处理）
            // - 如果有目标用户在本实例，只有全部推送成功才ACK
            if (!hasLocalUsers) {
                log.debug("No local users to process, ACK immediately: conversationId={}", message.getConversationId());
                return true; // 没有目标用户，直接ACK
            } else {
                if (allPushSuccess) {
                    log.debug("All local users processed successfully, ACK: conversationId={}",
                            message.getConversationId());
                    return true; // 全部推送成功，ACK
                } else {
                    log.warn("Some local users failed to process, not ACK: conversationId={}",
                            message.getConversationId());
                    return false; // 有推送失败，不ACK，让消息保留
                }
            }

        } catch (Exception e) {
            log.error("Failed to process message: {}", record.getId(), e);
            return false; // 处理失败，不ACK
        }
    }

    /**
     * 解析消息
     */
    private NotificationMessage parseMessage(StreamMessage<String, String> record) {
        try {
            String json = record.getBody().get("message");
            if (json == null) {
                // 兼容写入时是 Map 的情况
                // 此分支按需扩展，这里简化：当 message 字段不存在直接抛错
                throw new IllegalArgumentException("Missing field 'message' in stream record");
            }
            return objectMapper.readValue(json, NotificationMessage.class);
        } catch (Exception e) {
            log.error("Failed to parse message: {}", record.getId(), e);
            throw new RuntimeException("Failed to parse message", e);
        }
    }

    /**
     * 获取第一个非空值
     */
    @SuppressWarnings("unused")
    private String firstNonEmpty(String... values) { return null; }

    /**
     * Stream健康监控
     * 每5分钟检查一次Stream长度，防止消息积压
     */
    @Scheduled(fixedRate = 300000) // 5分钟
    public void monitorStreamHealth() {
        try {
            if (async == null || connection == null) {
                // 尚未初始化或已关闭，跳过本次监控
                return;
            }
            // 异步获取 Stream 长度（RedisFuture）
            io.lettuce.core.RedisFuture<Long> future = async.xlen(STREAM_KEY);

            future.whenComplete((streamLength, throwable) -> {
                if (throwable != null) {
                    log.error("Failed to monitor stream health (async)", throwable);
                    return;
                }
                if (streamLength == null) {
                    return;
                }

                double usageRatio = (double) streamLength / maxLength;

                if (usageRatio > 0.9) {
                    log.error("Stream length critical: {}/{} ({}%)", streamLength, maxLength,
                            String.format("%.1f", usageRatio * 100));
                    // 发送告警到企业微信
                    alertService.sendSystemErrorAlert("ChatStreamConsumer", "monitorStreamHealth",
                            String.format("Stream length critical: %d/%d (%.1f%%)", streamLength, maxLength,
                                    usageRatio * 100));
                } else if (usageRatio > 0.8) {
                    log.warn("Stream length high: {}/{} ({}%)", streamLength, maxLength,
                            String.format("%.1f", usageRatio * 100));
                } else {
                    log.debug("Stream length normal: {}/{} ({}%)", streamLength, maxLength,
                            String.format("%.1f", usageRatio * 100));
                }
            });

        } catch (Exception e) {
            // 提交异步任务本身失败（非 Redis 命令执行失败）
            log.error("Failed to submit async monitorStreamHealth task", e);
        }
    }

}
