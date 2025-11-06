package com.treasurehunt.chat.framework.core.websocket.distributed.config;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.StringCodec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Redis Lettuce 原生配置（支持单机和集群）
 * 用于直接操作 Redis Stream，绕开 Spring Data Redis 的限制
 */
@Configuration
public class RedisClusterLettuceConfig {

    /**
     * 创建 Redis 集群客户端（仅在配置了集群节点时生效）
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "spring.data.redis.cluster", name = "nodes")
    public RedisClusterClient redisClusterClient(RedisProperties props) {
        Duration timeout = props.getTimeout() != null ? props.getTimeout() : Duration.ofSeconds(7);
        List<RedisURI> uris = props.getCluster().getNodes().stream()
                .map(s -> {
                    String[] hp = s.split(":");
                    RedisURI.Builder b = RedisURI.Builder.redis(hp[0], Integer.parseInt(hp[1]))
                            .withTimeout(timeout);
                    if (props.getPassword() != null) {
                        b.withPassword(props.getPassword().toCharArray());
                    }
                    return b.build();
                })
                .collect(Collectors.toList());
        return RedisClusterClient.create(uris);
    }

    /**
     * 创建 Redis 单机客户端（当没有集群配置时生效）
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(RedisClusterClient.class)
    public RedisClient redisClient(RedisProperties props) {
        Duration timeout = props.getTimeout() != null ? props.getTimeout() : Duration.ofSeconds(7);
        RedisURI.Builder builder = RedisURI.Builder
                .redis(props.getHost(), props.getPort())
                .withDatabase(props.getDatabase())
                .withTimeout(timeout);// Lettuce 的命令超时

        if (props.getPassword() != null) {
            builder.withPassword(props.getPassword().toCharArray());
        }
        return RedisClient.create(builder.build());
    }

    /**
     * 创建集群连接（仅在集群模式下生效）
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "spring.data.redis.cluster", name = "nodes")
    public StatefulRedisClusterConnection<String, String> clusterConnection(RedisClusterClient client) {
        return client.connect(StringCodec.UTF8);
    }

    /**
     * 创建单机连接（仅在单机模式下生效）
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(StatefulRedisClusterConnection.class)
    public StatefulRedisConnection<String, String> redisConnection(RedisClient client) {
        return client.connect(StringCodec.UTF8);
    }
}
