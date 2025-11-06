package com.treasurehunt.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treasurehunt.chat.domain.ChatMessageDO;
import com.treasurehunt.chat.mapper.ChatMessageMapper;
import com.treasurehunt.chat.po.IdempRecord;
import com.treasurehunt.chat.po.IdempotencyCheckResult;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Random;

/**
 * 幂等性服务（方案A：Redis 先挡并发 → 占坑者写 DB → 标记 DONE → 其余并发读到 DONE 或少量回查 DB）
 * 
 * 目标：
 * 1. 快挡并发（短 TTL 占坑）- PENDING 状态，短 TTL ≈ 接口 P99 时延 × 2
 * 2. 结果复用（长 TTL 结果）- DONE 状态，长 TTL = 幂等窗口（1天/7天）
 * 3. 极端一致性靠 DB - 唯一索引兜底，反补缓存形成自愈闭环
 * 
 * 1. Redis 原子占坑（SETNX + 短TTL）
 * ↓
 * 2a. 成功占坑 → 继续业务入库 → 标记 DONE（长TTL）
 * ↓
 * 2b. 键已存在 → 自旋等待 DONE 结果（避免立即查DB）
 * ↓
 * 2c. 仍未得到结果 → 回查数据库 → 反补缓存
 * ↓
 * 2d. 数据库冲突 → 查询已存在记录 → 反补缓存
 */
@Slf4j
@Component
public class IdempotencyService {

    @Autowired
    private StatefulRedisConnection<String, String> redisConnection;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Random random = new Random();

    private static final String IDEMP_KEY_PREFIX = "chat:idemp:";

    /**
     * PENDING TTL（短）- 建议 = 接口 P99 × 2，常见 20s~120s
     * 覆盖绝大多数处理耗时，避免长时间堵同键并发
     */
    private static final long PENDING_TTL_MS = 60_000; // 60秒

    /**
     * DONE TTL（长）- 幂等窗口，常见 1天，金流/对账可 7~30 天
     */
    private static final long DONE_TTL_MS = 86_400_000; // 1天

    /**
     * 自旋等待的最大次数（避免立即查DB）
     */
    private static final int MAX_SPIN_RETRIES = 3;

    /**
     * 自旋等待的基础等待时间（毫秒）
     */
    private static final int BASE_SPIN_WAIT_MS = 50;

    /**
     * 自旋等待的最大抖动（毫秒）
     */
    private static final int MAX_SPIN_JITTER_MS = 100;

    /**
     * Lua 脚本：原子占坑（仅当不存在时写入 + 设置短 TTL）
     * 
     * KEYS[1] = idempKey
     * ARGV[1] = json(payload with status=PENDING, ts, owner)
     * ARGV[2] = pending_ttl_ms
     * 
     * 返回: "CREATED" 或 "EXISTS"
     */
    private static final String LUA_CREATE_PENDING = "if redis.call('EXISTS', KEYS[1]) == 0 then\n" +
            "  redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])\n" +
            "  return 'CREATED'\n" +
            "else\n" +
            "  return 'EXISTS'\n" +
            "end";

    /**
     * Lua 脚本：原子标记完成（覆盖为 DONE + 设置长 TTL）
     * 
     * KEYS[1] = idempKey
     * ARGV[1] = json(payload with status=DONE, result, ts)
     * ARGV[2] = done_ttl_ms
     * 
     * 返回: "OK"
     * 
     * 说明：即便键已过期也能"重建"为 DONE，避免"已成功但缓存缺失"的幂等黑洞
     */
    private static final String LUA_MARK_DONE = "redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])\n" +
            "return 'OK'";

    /**
     * 检查幂等性并处理（方案A的核心方法）
     * 
     * @param conversationId 会话ID
     * @param clientMsgId    客户端消息ID
     * @return 检查结果，如果 duplicateFound=true 则包含 serverMsgId
     */
    public IdempotencyCheckResult checkBeforePersist(String conversationId, String clientMsgId) {
        final String idempKey = IDEMP_KEY_PREFIX + conversationId + ":" + clientMsgId;

        try {
            RedisCommands<String, String> commands = redisConnection.sync();
            String owner = generateOwner();

            // 1. 原子占坑
            String createResult = evalLuaCreate(commands, idempKey,
                    IdempRecord.pending(owner), PENDING_TTL_MS);

            if ("CREATED".equals(createResult)) {
                // 我是首个请求：返回 duplicateFound=false，让调用方继续执行业务入库
                log.debug("成功占坑，我是首个请求: idempKey={}, owner={}", idempKey, owner);
                return IdempotencyCheckResult.builder()
                        .duplicateFound(false)
                        .usedRedis(true)
                        .build();
            }

            // 2. 我不是首个请求（键已存在）
            // 2.1 先做短暂"自旋"等待，尽量命中首个请求写回的 DONE，避免打 DB
            IdempRecord record = spinWaitForResult(commands, idempKey, MAX_SPIN_RETRIES);

            if (record != null && "DONE".equals(record.getStatus()) && record.isNumericResult()) {
                // 快路径：在自旋中等到 DONE 结果
                Long serverMsgId = Long.parseLong(record.getResult());
                log.debug("自旋等待命中快路径: idempKey={}, serverMsgId={}", idempKey, serverMsgId);
                return IdempotencyCheckResult.builder()
                        .duplicateFound(true)
                        .serverMsgId(serverMsgId)
                        .usedRedis(true)
                        .build();
            }

            // 2.2 仍未得到结果：以 DB 为准（唯一索引保证不重复）
            log.debug("自旋等待未命中，回查数据库: idempKey={}", idempKey);
            ChatMessageDO existed = chatMessageMapper.selectByConvIdAndClientMsgId(conversationId, clientMsgId);

            if (existed != null) {
                // 反补缓存，形成自愈闭环
                Long serverMsgId = existed.getServerMsgId();
                evalLuaDone(commands, idempKey, IdempRecord.done(serverMsgId, owner), DONE_TTL_MS);
                log.debug("数据库查询到结果并反补缓存: idempKey={}, serverMsgId={}", idempKey, serverMsgId);
                return IdempotencyCheckResult.builder()
                        .duplicateFound(true)
                        .serverMsgId(serverMsgId)
                        .usedRedis(false) // 这次查询用了DB，但后续会走Redis
                        .build();
            }

            // 2.3 没查到：可能是首个请求正在处理中，或者首个请求失败了
            // 返回 duplicateFound=false，让当前请求尝试处理（DB唯一索引会兜底）
            log.warn("自旋等待和数据库查询都未找到结果，可能首个请求失败或超时: idempKey={}", idempKey);
            return IdempotencyCheckResult.builder()
                    .duplicateFound(false)
                    .usedRedis(true)
                    .build();

        } catch (Exception ex) {
            log.warn("Redis idempotency check failed, fallback to DB. convId={}, clientMsgId={}",
                    conversationId, clientMsgId, ex);

            // Redis 失败时，完全依赖 DB（唯一索引兜底）
            ChatMessageDO existed = chatMessageMapper.selectByConvIdAndClientMsgId(conversationId, clientMsgId);
            if (existed != null) {
                return IdempotencyCheckResult.builder()
                        .duplicateFound(true)
                        .serverMsgId(existed.getServerMsgId())
                        .usedRedis(false)
                        .build();
            }

            // DB 也没查到，返回非重复，让业务继续（DB 唯一索引会兜底）
            return IdempotencyCheckResult.builder()
                    .duplicateFound(false)
                    .usedRedis(false)
                    .build();
        }
    }

    /**
     * 标记成功（占坑者入库成功后调用）
     * 
     * @param conversationId 会话ID
     * @param clientMsgId    客户端消息ID
     * @param serverMsgId    服务端消息ID（入库成功后的结果）
     */
    public void markSuccess(String conversationId, String clientMsgId, Long serverMsgId) {
        final String idempKey = IDEMP_KEY_PREFIX + conversationId + ":" + clientMsgId;

        try {
            RedisCommands<String, String> commands = redisConnection.sync();
            String owner = generateOwner();
            evalLuaDone(commands, idempKey, IdempRecord.done(serverMsgId, owner), DONE_TTL_MS);
            log.debug("标记幂等成功: idempKey={}, serverMsgId={}", idempKey, serverMsgId);
        } catch (Exception ex) {
            log.warn("标记幂等成功失败（不影响业务）: idempKey={}, serverMsgId={}", idempKey, serverMsgId, ex);
            // 失败不影响业务，后续请求会通过DB反补
        }
    }

    /**
     * 处理数据库唯一索引冲突后的回查和反补
     * 当业务入库时遇到 DuplicateKeyException 时调用此方法
     * 
     * @param conversationId 会话ID
     * @param clientMsgId    客户端消息ID
     * @return 查询到的 serverMsgId，如果查不到则返回 null
     */
    public Long handleDuplicateKeyConflict(String conversationId, String clientMsgId) {
        final String idempKey = IDEMP_KEY_PREFIX + conversationId + ":" + clientMsgId;

        try {
            // 从数据库查询已存在的记录
            ChatMessageDO existed = chatMessageMapper.selectByConvIdAndClientMsgId(conversationId, clientMsgId);
            if (existed != null) {
                Long serverMsgId = existed.getServerMsgId();

                // 反补缓存
                RedisCommands<String, String> commands = redisConnection.sync();
                String owner = generateOwner();
                evalLuaDone(commands, idempKey, IdempRecord.done(serverMsgId, owner), DONE_TTL_MS);
                log.debug("处理唯一索引冲突后反补缓存: idempKey={}, serverMsgId={}", idempKey, serverMsgId);
                return serverMsgId;
            }
        } catch (Exception ex) {
            log.warn("处理唯一索引冲突时出错: idempKey={}", idempKey, ex);
        }

        return null;
    }

    /**
     * 执行 Lua 脚本：原子占坑
     */
    private String evalLuaCreate(RedisCommands<String, String> commands, String idempKey,
            IdempRecord record, long ttlMs) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(record);
            return (String) commands.eval(LUA_CREATE_PENDING,
                    io.lettuce.core.ScriptOutputType.VALUE,
                    new String[] { idempKey },
                    jsonPayload, String.valueOf(ttlMs));
        } catch (Exception e) {
            log.error("执行 Lua 占坑脚本失败: idempKey={}", idempKey, e);
            throw new RuntimeException("Redis Lua script execution failed", e);
        }
    }

    /**
     * 执行 Lua 脚本：原子标记完成
     */
    private void evalLuaDone(RedisCommands<String, String> commands, String idempKey,
            IdempRecord record, long ttlMs) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(record);
            commands.eval(LUA_MARK_DONE,
                    io.lettuce.core.ScriptOutputType.STATUS,
                    new String[] { idempKey },
                    jsonPayload, String.valueOf(ttlMs));
        } catch (Exception e) {
            log.error("执行 Lua 标记完成脚本失败: idempKey={}", idempKey, e);
            throw new RuntimeException("Redis Lua script execution failed", e);
        }
    }

    /**
     * 自旋等待结果（带抖动避免放大效应）
     * 
     * @param commands   Redis 命令
     * @param idempKey   幂等键
     * @param maxRetries 最大重试次数
     * @return 如果读到 DONE 状态返回记录，否则返回 null
     */
    private IdempRecord spinWaitForResult(RedisCommands<String, String> commands,
            String idempKey, int maxRetries) {
        for (int i = 0; i < maxRetries; i++) {
            try {
                String cached = commands.get(idempKey);
                if (cached == null) {
                    // 键过期了，跳转到 DB 查询
                    break;
                }

                IdempRecord record = parseRecord(cached);
                if (record == null) {
                    break;
                }

                if ("DONE".equals(record.getStatus()) && record.isNumericResult()) {
                    // 等到 DONE 结果，立即返回
                    return record;
                }

                if ("PENDING".equals(record.getStatus())) {
                    // 还在处理中，等待一下再重试（带抖动）
                    int waitMs = BASE_SPIN_WAIT_MS + random.nextInt(MAX_SPIN_JITTER_MS);
                    try {
                        Thread.sleep(waitMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }

                // 其他状态，跳出循环
                break;

            } catch (Exception e) {
                log.warn("自旋等待时出错: idempKey={}, retry={}", idempKey, i, e);
                break;
            }
        }

        return null;
    }

    /**
     * 解析 IdempRecord JSON
     */
    private IdempRecord parseRecord(String json) {
        try {
            return objectMapper.readValue(json, IdempRecord.class);
        } catch (Exception e) {
            log.warn("解析 IdempRecord JSON 失败: json={}", json, e);
            return null;
        }
    }

    /**
     * 生成占坑者标识（用于排障）
     * 格式：host:pid:traceId（简化版：host:pid:timestamp）
     */
    private String generateOwner() {
        try {
            String host = java.net.InetAddress.getLocalHost().getHostName();
            long pid = ProcessHandle.current().pid();
            long traceId = System.currentTimeMillis() % 1000000; // 简化版 traceId
            return host + ":" + pid + ":" + traceId;
        } catch (Exception e) {
            return "unknown:" + System.currentTimeMillis();
        }
    }
}
