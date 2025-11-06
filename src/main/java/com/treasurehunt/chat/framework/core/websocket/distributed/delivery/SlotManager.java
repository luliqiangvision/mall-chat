package com.treasurehunt.chat.framework.core.websocket.distributed.delivery;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Instant;

/**
 * 槽位分配与续租管理（基于 Redis 的轻量租约机制）
 *
 * 约定：
 * - 分配的槽位号范围为 [0, slotCount-1]
 * - 采用锁 + 租约键的方式实现占用与续期
 * - 当续租失败（owner 变化或租约键被他人覆盖）时，发布 SlotLeaseLostEvent
 */
@Slf4j
@Component
public class SlotManager {

    @Autowired
    private RedisClient redisClient;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Value("${spring.application.name}")
    private String serviceName;

    /**
     * 槽位总数（放在 websocket.instance 下，由配置中心下发），不设置默认值
     */
    @Value("${websocket.instance.count}")
    private int slotCount;

    /** 续约时长（秒） */
    @Value("${chat.stream.slot-lease-seconds:30}")
    private int leaseSeconds;

    /** 心跳间隔（毫秒） */
    @Value("${chat.stream.slot-heartbeat-ms:10000}")
    private long heartbeatMs;

    private StatefulRedisConnection<String, String> connection;
    private RedisCommands<String, String> sync;

    private volatile Integer heldSlotId;
    private volatile String currentOwnerId;

    @PostConstruct
    public void init() {
        try {
            this.connection = redisClient.connect();
            this.sync = connection.sync();
        } catch (Throwable t) {
            log.warn("SlotManager init redis connection failed, will fallback when allocate", t);
            this.connection = null;
            this.sync = null;
        }
    }

    @PreDestroy
    public void destroy() {
        try {
            if (connection != null) connection.close();
        } catch (Throwable ignore) {}
    }

    public Integer getHeldSlotId() { return heldSlotId; }

    public boolean isRedisAvailable() { return sync != null; }

    /**
     * 抢占槽位（若 slotCount<=0 或 redis 不可用，返回 null 让调用方走本地兜底）
     */
    public Integer acquireSlot(String ownerId) {
        final int total = Math.max(1, slotCount); // 至少为1，count=1时仅允许slot=0
        if (total <= 0 || sync == null) {
            log.error("Slot allocation unavailable: service={}, slotCount={}, redisAvailable={}",
                    serviceName, total, sync != null);
            return null;
        }
        // 如果已持有并尝试续租
        if (heldSlotId != null) {
            renewLease(ownerId);
            return heldSlotId;
        }
        for (int k = 0; k < total; k++) {
            String lockKey = keyLock(k);
            String ok = null;
            try {
                ok = sync.set(lockKey, ownerId, io.lettuce.core.SetArgs.Builder.nx().px(3000));
            } catch (Exception e) {
                log.warn("Acquire slot lock error: slot={}", k, e);
                continue;
            }
            if (!"OK".equals(ok)) {
                continue;
            }
            try {
                String leaseKey = keyLease(k);
                String leaseOwner = sync.get(leaseKey);
                if (leaseOwner == null) {
                    // 空闲，直接占用
                    sync.setex(leaseKey, leaseSeconds, ownerId);
                    heldSlotId = k;
                    currentOwnerId = ownerId;
                    log.info("Slot acquired: service={}, slot={}, owner={}", serviceName, k, ownerId);
                    return heldSlotId;
                } else if (ownerId.equals(leaseOwner)) {
                    // 自己的租约（重启后的续持）
                    sync.setex(leaseKey, leaseSeconds, ownerId);
                    heldSlotId = k;
                    currentOwnerId = ownerId;
                    log.info("Slot re-acquired: service={}, slot={}, owner={}", serviceName, k, ownerId);
                    return heldSlotId;
                } else {
                    // 已被他人占用
                }
            } finally {
                try { sync.del(lockKey); } catch (Exception ignore) {}
            }
        }
        log.error("Failed to acquire any slot: service={}, slotCount(normalized)={}, owner={}",
                serviceName, total, ownerId);
        return null;
    }

    /** 定时续租，失败时发布事件，提示调用方重建消费上下文 */
    @Scheduled(fixedDelayString = "${chat.stream.slot-heartbeat-ms:10000}")
    public void heartbeat() {
        if (sync == null || heldSlotId == null || currentOwnerId == null) return;
        try {
            String leaseKey = keyLease(heldSlotId);
            String leaseOwner = sync.get(leaseKey);
            if (currentOwnerId.equals(leaseOwner)) {
                sync.setex(leaseKey, leaseSeconds, currentOwnerId);
            } else {
                log.warn("Slot lease lost: service={}, slot={}, expectedOwner={}, actual={}",
                        serviceName, heldSlotId, currentOwnerId, leaseOwner);
                Integer lost = heldSlotId;
                heldSlotId = null;
                currentOwnerId = null;
                eventPublisher.publishEvent(new SlotLeaseLostEvent(this, serviceName, lost));
            }
        } catch (Exception e) {
            log.warn("Slot lease renew failed at {}: service={}, slot={}", Instant.now(), serviceName, heldSlotId, e);
        }
    }

    private void renewLease(String ownerId) {
        if (sync == null || heldSlotId == null) return;
        try {
            String leaseKey = keyLease(heldSlotId);
            String leaseOwner = sync.get(leaseKey);
            if (ownerId.equals(leaseOwner)) {
                sync.setex(leaseKey, leaseSeconds, ownerId);
            }
        } catch (Exception e) {
            log.warn("renewLease error: service={}, slot={}", serviceName, heldSlotId, e);
        }
    }

    private String keyLease(int k) { return "chat:slot:" + serviceName + ":" + k + ":lease"; }
    private String keyLock(int k) { return "chat:slot:" + serviceName + ":" + k + ":lock"; }
}


