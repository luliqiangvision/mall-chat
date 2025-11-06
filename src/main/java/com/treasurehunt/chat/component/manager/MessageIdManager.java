package com.treasurehunt.chat.component.manager;

import com.treasurehunt.chat.mapper.ChatMessageMapper;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class MessageIdManager {

    private static final String REDIS_KEY_PREFIX = "chat:serverMsgId:";
    private static final String LOCK_KEY_PREFIX = "lock:chat:serverMsgId:";

    @Autowired
    private StatefulRedisConnection<String, String> redisConnection;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    /**
     * 懒初始化 + 允许空洞：仅在会话第一次需要递增且 Redis 无 key 时，从数据库读取最大 server_msg_id 做初始化。
     * Redis 不可用时抛出异常并记录错误,然后采用降级处理,也就是自己根据server_msg_id字段自增,只是插入数据库的地方,要
     * 采用新的方式插入,onduplicate的那种方式,如果有onduplicate,就自动加1,也就是当前方法要返回redis的状态,如果redis真的
     * 不可用了,插入的方式就是onduplicate自动加1的方式了,否则还是原来的方式,防止重复处理消息,也就是真的不可用的时候,要牺牲幂等性了
     */
    public MessageIdGenerateResult generateServerMsgId(String conversationId) {
        if (conversationId == null || conversationId.isEmpty()) {
            throw new IllegalArgumentException("conversationId must not be empty");
        }

        final String key = REDIS_KEY_PREFIX + conversationId;

        // 1) 快路径：已有 key，直接自增（仅包裹 Redis 操作）
        try {
            RedisCommands<String, String> commands = redisConnection.sync();
            if (commands.exists(key) > 0) {
                Long next = commands.incr(key);
                if (next == null) {
                    throw new IllegalStateException("Redis INCR returned null");
                }
                return MessageIdGenerateResult.builder()
                        .redisAvailable(true)
                        .serverMsgId(next)
                        .build();
            }
        } catch (Exception ex) {
            log.error("Redis quick-path failed, will fallback. conversationId={}", conversationId, ex);
            Long maxId = safeQueryMaxId(conversationId);
            Long fallback = (maxId == null) ? 1L : (maxId + 1L);
            return MessageIdGenerateResult.builder()
                    .redisAvailable(false)
                    .serverMsgId(fallback)
                    .build();
        }

        // 2) 慢路径：首次初始化，需要 DB 的 maxId；DB 查询放在 Redis try-catch 之外
        Long maxId = safeQueryMaxId(conversationId);

        try {
            final String lockName = LOCK_KEY_PREFIX + conversationId;
            RLock lock = redissonClient.getLock(lockName);
            lock.lock(10, TimeUnit.SECONDS);
            try {
                RedisCommands<String, String> commands = redisConnection.sync();
                if (commands.exists(key) > 0) {
                    Long next = commands.incr(key);
                    if (next == null) {
                        throw new IllegalStateException("Redis INCR returned null");
                    }
                    return MessageIdGenerateResult.builder()
                            .redisAvailable(true)
                            .serverMsgId(next)
                            .build();
                }

                String initial = (maxId != null) ? String.valueOf(maxId) : "0";
                commands.set(key, initial);
                Long next = commands.incr(key);
                if (next == null) {
                    throw new IllegalStateException("Redis INCR returned null after initialization");
                }
                return MessageIdGenerateResult.builder()
                        .redisAvailable(true)
                        .serverMsgId(next)
                        .build();
            } finally {
                try {
                    lock.unlock();
                } catch (Exception unlockEx) {
                    log.warn("Failed to unlock Redisson lock for conversationId={}", conversationId, unlockEx);
                }
            }
        } catch (Exception ex) {
            log.error("Redis init-path failed, downgrade. conversationId={}", conversationId, ex);
            Long fallback = (maxId == null) ? 1L : (maxId + 1L);
            return MessageIdGenerateResult.builder()
                    .redisAvailable(false)
                    .serverMsgId(fallback)
                    .build();
        }
    }

    /**
     * 探活/只读查询：返回会话当前已知的最大 serverMsgId。
     * 行为：优先从 Redis 读取；失败或没有则查询 DB 的 MAX(server_msg_id)。
     * 不做任何写入、加锁或自增。
     */
    public long probeLatestServerMsgId(String conversationId) {
        if (conversationId == null || conversationId.isEmpty()) {
            throw new IllegalArgumentException("conversationId must not be empty");
        }

        final String key = REDIS_KEY_PREFIX + conversationId;

        // 1) Redis 优先：GET 当前最大值
        try {
            RedisCommands<String, String> commands = redisConnection.sync();
            String val = commands.get(key);
            if (val != null && !val.isEmpty()) {
                try {
                    return Long.parseLong(val);
                } catch (NumberFormatException nfe) {
                    log.warn("Invalid long in redis for key={}, value={}", key, val);
                }
            }
        } catch (Exception ex) {
            log.warn("Redis GET failed for key={}, will fallback to DB", key, ex);
        }

        // 2) Fallback：查询数据库最大 server_msg_id；无记录则 0
        Long maxId = safeQueryMaxId(conversationId);
        return (maxId != null) ? maxId : 0L;
    }

    private Long safeQueryMaxId(String conversationId) {
        try {
            return chatMessageMapper.getMaxServerMsgIdByConvId(conversationId);
        } catch (Exception ex) {
            log.warn("Query max server_msg_id failed for conversationId={}", conversationId, ex);
            return null;
        }
    }
}


