package com.treasurehunt.chat.framework.core.websocket.distributed.delivery;

import io.lettuce.core.XAddArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Redis Stream 原生操作客户端
 * 使用 Lettuce 原生 API 直接操作 Redis Stream，支持 XADD MAXLEN ~ 和 XTRIM ~ 命令
 * 支持单机和集群两种模式
 */
@Slf4j
@Service
public class ChatStreamClient {

    private final StatefulRedisConnection<String, String> singleConn;
    private final StatefulRedisClusterConnection<String, String> clusterConn;
    private final boolean isClusterMode;

    public ChatStreamClient(@Autowired(required = false) StatefulRedisConnection<String, String> singleConn,
                           @Autowired(required = false) StatefulRedisClusterConnection<String, String> clusterConn) {
        this.singleConn = singleConn;
        this.clusterConn = clusterConn;
        this.isClusterMode = clusterConn != null;
        
        if (singleConn == null && clusterConn == null) {
            throw new IllegalStateException("Neither single nor cluster Redis connection is available");
        }
    }

    @Value("${chat.stream.key:chat:{global}}")   // 使用哈希标签确保槽位稳定
    private String streamKey;

    @Value("${chat.stream.max-length:100000}")
    private long maxLength;

    /**
     * 写入群聊消息（广播流）：
     * XADD key MAXLEN ~ N * field value ...
     * 
     * @param body 消息体，包含所有字段
     * @return 消息ID
     */
    public String addBroadcast(Map<String, String> body) {
        try {
            String messageId;
            if (isClusterMode) {
                RedisAdvancedClusterCommands<String, String> cmd = clusterConn.sync();
                messageId = cmd.xadd(
                        streamKey,
                        XAddArgs.Builder.maxlen(maxLength).approximateTrimming(), // ==> MAXLEN ~ N
                        body
                );
            } else {
                RedisCommands<String, String> cmd = singleConn.sync();
                messageId = cmd.xadd(
                        streamKey,
                        XAddArgs.Builder.maxlen(maxLength).approximateTrimming(), // ==> MAXLEN ~ N
                        body
                );
            }
            log.debug("Added message to stream {} with ID: {}", streamKey, messageId);
            return messageId;
        } catch (Exception e) {
            log.error("Failed to add message to stream {}: {}", streamKey, e.getMessage(), e);
            throw new RuntimeException("Failed to add message to stream", e);
        }
    }


    /**
     * 获取当前流长度：XLEN key
     * 
     * @return 流中的消息数量
     */
    public long length() {
        try {
            long len;
            if (isClusterMode) {
                len = clusterConn.sync().xlen(streamKey);
            } else {
                len = singleConn.sync().xlen(streamKey);
            }
            log.debug("Stream {} length: {}", streamKey, len);
            return len;
        } catch (Exception e) {
            log.error("Failed to get stream length for {}: {}", streamKey, e.getMessage(), e);
            return 0;
        }
    }

    /**
     * 检查流是否存在
     * 
     * @return true 如果流存在
     */
    public boolean exists() {
        try {
            boolean exists;
            if (isClusterMode) {
                exists = clusterConn.sync().exists(streamKey) > 0;
            } else {
                exists = singleConn.sync().exists(streamKey) > 0;
            }
            return exists;
        } catch (Exception e) {
            log.error("Failed to check stream existence for {}: {}", streamKey, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 删除整个流
     * 
     * @return true 如果删除成功
     */
    public boolean delete() {
        try {
            long deleted;
            if (isClusterMode) {
                deleted = clusterConn.sync().del(streamKey);
            } else {
                deleted = singleConn.sync().del(streamKey);
            }
            log.info("Deleted stream {}: {} keys removed", streamKey, deleted);
            return deleted > 0;
        } catch (Exception e) {
            log.error("Failed to delete stream {}: {}", streamKey, e.getMessage(), e);
            return false;
        }
    }
}
