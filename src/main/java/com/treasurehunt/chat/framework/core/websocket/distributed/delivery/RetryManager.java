package com.treasurehunt.chat.framework.core.websocket.distributed.delivery;

import com.treasurehunt.chat.framework.core.websocket.distributed.spi.ServerCommProtocolManager;
import io.netty.util.HashedWheelTimer;
import io.netty.util.concurrent.DefaultThreadFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.Set;

/**
 * 重试管理器
 * 
 * 职责：
 * - 管理消息发送的重试逻辑
 * - 支持 Nacos 配置的重试策略
 * - 处理 sessionId 变化后的重试
 * 
 * @author gaga
 * @since 2025-10-06
 */
@Slf4j
@Component
public class RetryManager {

	@Autowired
	private ServerCommProtocolManager protocolManager;

	@Autowired
	private UserSessionMetadataManager userSessionMetadataManager;

	@Autowired
	private NacosConfigService nacosConfigService;

	@Autowired(required = false)
	private AlertService alertService; // 注入告警服务（可选）

	@Autowired
	private NotifyPushSender notifyPushSender;

	/**
	 * 默认指数退避延迟序列（单位：毫秒），在未从配置中心获取到自定义序列时使用。
	 */
	private static final List<Long> DEFAULT_RETRY_DELAYS = List.of(1000L, 2000L, 4000L, 8000L, 16000L);
	/**
	 * 时间轮刻度时长（单位：毫秒）。此处采用 1000ms（1 秒）刻度，兼顾调度粒度与线程开销。
	 */
	private static final long DEFAULT_TICK_DURATION_MS = 1000L;
	/**
	 * 时间轮槽位数量。tickDuration × ticksPerWheel 决定单轮能够覆盖的最长延迟（当前约 128 秒）。
	 */
	private static final int DEFAULT_TICKS_PER_WHEEL = 128;

	/**
	 * 承载消息重试任务的时间轮实例。
	 */
	private HashedWheelTimer retryTimer;

	@PostConstruct
	public void initTimer() {
		retryTimer = new HashedWheelTimer(
				new DefaultThreadFactory("notification-retry-wheel", true),
				DEFAULT_TICK_DURATION_MS,
				TimeUnit.MILLISECONDS,
				DEFAULT_TICKS_PER_WHEEL);
		retryTimer.start();
		log.info("Notification retry timer initialized: tick={}ms, slots={}", DEFAULT_TICK_DURATION_MS, DEFAULT_TICKS_PER_WHEEL);
	}

	@PreDestroy
	public void shutdownTimer() {
		if (retryTimer != null) {
			try {
				retryTimer.stop();
				log.info("Notification retry timer stopped");
			} catch (Exception e) {
				log.warn("Failed to stop retry timer cleanly", e);
			}
		}
	}

	/**
	 * 执行带重试的消息发送
	 * @param userId 用户ID
	 * @param message 消息内容
	 * @param targetInstanceAddress 目标实例地址 (IP:Port)
	 */
	public void executeWithRetry(String userId, NotificationMessage message, String targetInstanceAddress) {
		RetryConfig config = getRetryConfig();

		if (!config.isEnabled()) {
			log.debug("Retry disabled, skip push retry for user: {}", userId);
			return;
		}

		List<Long> retryDelays = resolveRetryDelays(config);
		if (retryDelays.isEmpty()) {
			log.debug("Retry config has no delay entries, executing single attempt for user: {}", userId);
		}

		RetryContext context = new RetryContext(userId, message, targetInstanceAddress, retryDelays);
		performAttempt(context, 0);
	}

	private void performAttempt(RetryContext context, int attemptIndex) {
		final int displayAttempt = attemptIndex + 1;
		final String userId = context.getUserId();

		try {
			String currentInstanceAddress = userSessionMetadataManager.getInstanceAddress(userId);
			if (currentInstanceAddress != null && !currentInstanceAddress.equals(context.getLastInstanceAddress())) {
				log.info("User {} instance changed from {} to {}", userId, context.getLastInstanceAddress(), currentInstanceAddress);
				context.setLastInstanceAddress(currentInstanceAddress);
			}

			CompletableFuture<Boolean> sendResult = sendMessage(userId, context.getMessage(), context.getLastInstanceAddress());
			sendResult.whenComplete((success, throwable) -> {
				if (Boolean.TRUE.equals(success)) {
					log.debug("Message sent successfully on attempt {} for user: {}", displayAttempt, userId);
					return;
				}

				if (throwable != null) {
					log.warn("Send failed on attempt {} for user {}: {}", displayAttempt, userId, throwable.getMessage(), throwable);
					if (alertService != null) {
						alertService.sendSystemErrorAlert("RetryManager", "sendMessage", throwable.getMessage());
					}
				} else {
					log.debug("Message send returned false on attempt {} for user {}", displayAttempt, userId);
				}

				scheduleNextAttempt(context, attemptIndex);
			});
		} catch (Exception e) {
			log.warn("Send failed on attempt {} for user {}: {}", displayAttempt, userId, e.getMessage(), e);
			if (alertService != null) {
				alertService.sendSystemErrorAlert("RetryManager", "sendMessage", e.getMessage());
			}
			scheduleNextAttempt(context, attemptIndex);
		}
	}

	private void scheduleNextAttempt(RetryContext context, int completedAttempts) {
		int nextAttemptIndex = completedAttempts + 1;
		List<Long> delays = context.getRetryDelays();

		if (nextAttemptIndex > delays.size()) {
			handleFinalFailure(context, completedAttempts + 1);
			return;
		}

		long delayMillis = delays.get(nextAttemptIndex - 1);
		log.debug("Schedule retry {} for user {} after {} ms", nextAttemptIndex + 1, context.getUserId(), delayMillis);

		Runnable task = () -> performAttempt(context, nextAttemptIndex);

		if (retryTimer == null) {
			log.warn("Retry timer not initialized, fallback to delayed executor for user {}", context.getUserId());
			CompletableFuture.delayedExecutor(delayMillis, TimeUnit.MILLISECONDS).execute(task);
			return;
		}

		try {
			retryTimer.newTimeout(timeout -> {
				if (!timeout.isCancelled()) {
					task.run();
				}
			}, delayMillis, TimeUnit.MILLISECONDS);
		} catch (IllegalStateException ex) {
			log.warn("Retry timer unavailable, run attempt {} via fallback executor for user {}", nextAttemptIndex + 1, context.getUserId(), ex);
			CompletableFuture.delayedExecutor(delayMillis, TimeUnit.MILLISECONDS).execute(task);
		}
	}

	private void handleFinalFailure(RetryContext context, int totalAttempts) {
		log.error("Message push failed after {} attempts for user {}, conversationId={}, serverMsgId={}",
				totalAttempts,
				context.getUserId(),
				context.getMessage().getConversationId(),
				context.getMessage().getServerMsgId());
		if (alertService != null) {
			alertService.sendPushFailureAlert(
					context.getUserId(),
					context.getMessage().getConversationId(),
					context.getMessage().getServerMsgId(),
					totalAttempts,
					"Max retries reached");
		}
	}

	private List<Long> resolveRetryDelays(RetryConfig config) {
		if (config.getRetryDelays() != null && !config.getRetryDelays().isEmpty()) {
			List<Long> sanitized = config.getRetryDelays().stream()
					.filter(delay -> delay != null && delay > 0)
					.toList();
			if (!sanitized.isEmpty()) {
				return sanitized;
			}
		}

		if (config.isInfiniteRetry()) {
			log.warn("Infinite retry is not supported in time-wheel scheduler, fallback to default delays: {}", DEFAULT_RETRY_DELAYS);
			return DEFAULT_RETRY_DELAYS;
		}

		int retries = Math.max(config.getMaxRetries(), 0);
		if (retries == 0) {
			return Collections.emptyList();
		}

		long firstDelay = config.getRetryInterval() > 0 ? config.getRetryInterval() : DEFAULT_RETRY_DELAYS.get(0);
		double multiplier = config.getBackoffMultiplier() > 0 ? config.getBackoffMultiplier() : 2.0d;
		long maxDelay = config.getMaxRetryInterval() > 0 ? config.getMaxRetryInterval() : DEFAULT_RETRY_DELAYS.get(DEFAULT_RETRY_DELAYS.size() - 1);

		List<Long> delays = new ArrayList<>(retries);
		long delay = firstDelay;
		for (int i = 0; i < retries; i++) {
			long boundedDelay = Math.min(delay, maxDelay);
			delays.add(Math.max(1L, boundedDelay));
			double nextDelay = boundedDelay * multiplier;
			delay = (long) Math.max(1L, Math.min(nextDelay, (double) maxDelay));
		}
		return Collections.unmodifiableList(delays);
	}

	/**
	 * 发送消息
	 * @param userId 用户ID
	 * @param message 消息内容
	 * @param targetInstanceAddress 目标实例地址 (IP:Port)
	 * @return 发送结果
	 */
    private CompletableFuture<Boolean> sendMessage(String userId, NotificationMessage message, String targetInstanceAddress) {
        if (targetInstanceAddress == null) {
            log.debug("User {} is offline, cannot send message, conversationId={}, serverMsgId={}", userId, message.getConversationId(),message.getServerMsgId());
            return CompletableFuture.completedFuture(false);
        }

        return protocolManager.sendMessage(targetInstanceAddress, message)
            .thenCompose(result -> {
                if (result == null) return CompletableFuture.completedFuture(false);
                if (result.isSuccess()) return CompletableFuture.completedFuture(true);
                if (result.getCode() == SendResult.SendCode.SELF_TARGET) {
                    // 本机直发：找到本机会话并发送
                    Set<String> sessionIds = userSessionMetadataManager.getSessionIdsByUserId(userId);
                    if (sessionIds == null || sessionIds.isEmpty()) return CompletableFuture.completedFuture(false);
                    boolean anySuccess = false;
                    for (String sid : sessionIds) {
                        WebSocketSession session = userSessionMetadataManager.getLocalSession(sid);
                        if (session != null) {
                            boolean ok = serverCommLocalSend(session, message);
                            anySuccess = anySuccess || ok;
                        }
                    }
                    return CompletableFuture.completedFuture(anySuccess);
                }
				log.error("Send failed for user {}: {}, conversationId={}, serverMsgId={}, code={}", userId, message.getConversationId(),message.getServerMsgId(),result.getCode());
                return CompletableFuture.completedFuture(false);
            });
    }


    /**
	 * 单聊模式下,客服和客户都连到同一台服务器了,直接推送到客户端,服务器之间就不需要单聊的转发了
	 * @param session
	 * @param message
	 * @return
	 */
    private boolean serverCommLocalSend(WebSocketSession session, NotificationMessage message) {
        try {
            notifyPushSender.sendNotifyPullLocal(
                    session.getId(),
                    message.getConversationId(),
                    message.getServerMsgId()
            );
            return true;
        } catch (Exception e) {
            log.error("Failed to send message via local session: {}, conversationId={}, serverMsgId={}", 
                    session != null ? session.getId() : null, message.getConversationId(), message.getServerMsgId(), e);
            return false;
        }
    }

	/**
	 * 获取重试配置
	 * @return 重试配置
	 */
	private RetryConfig getRetryConfig() {
		try {
			String configJson = nacosConfigService.getConfig("websocket-distributed-notification-retry-config", "DEFAULT_GROUP", 5000);
			if (configJson != null && !configJson.isEmpty()) {
				// 解析 JSON 配置
				com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
				return mapper.readValue(configJson, RetryConfig.class);
			}
		} catch (Exception e) {
			log.warn("Failed to get retry config from Nacos, using default", e);
		}
		
		// 返回默认配置
		return new RetryConfig();
	}

	private static final class RetryContext {
		/**
		 * 消息所属用户标识。
		 */
		private final String userId;
		/**
		 * 待推送的消息内容，包含会话信息与消息 ID。
		 */
		private final NotificationMessage message;
		/**
		 * 每次重试对应的延迟列表（毫秒）。
		 */
		private final List<Long> retryDelays;
		/**
		 * 最近一次检测到的目标实例地址，便于处理会话漂移。
		 */
		private volatile String lastInstanceAddress;

		RetryContext(String userId, NotificationMessage message, String initialAddress, List<Long> retryDelays) {
			this.userId = userId;
			this.message = message;
			this.retryDelays = retryDelays;
			this.lastInstanceAddress = initialAddress;
		}

		String getUserId() {
			return userId;
		}

		NotificationMessage getMessage() {
			return message;
		}

		List<Long> getRetryDelays() {
			return retryDelays;
		}

		String getLastInstanceAddress() {
			return lastInstanceAddress;
		}

		void setLastInstanceAddress(String lastInstanceAddress) {
			this.lastInstanceAddress = lastInstanceAddress;
		}
	}
}
