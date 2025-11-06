package com.treasurehunt.chat.framework.core.websocket.distributed.delivery;

import lombok.Data;

import java.util.List;

/**
 * 重试配置
 * 
 * 默认采用指数退避（1s、2s、4s、8s、16s）并限制为 5 次重试。
 * 可通过 Nacos 动态调整。
 */
@Data
public class RetryConfig {
    private boolean enabled = true;
    /**
     * 最大重试次数（不含首轮立即尝试）
     */
    private int maxRetries = 5;
    /**
     * 首次重试延迟（毫秒）
     */
    private long retryInterval = 1000;
    /**
     * 最大延迟阈值（毫秒）
     */
    private long maxRetryInterval = 16000;
    /**
     * 退避倍率
     */
    private double backoffMultiplier = 2.0;
    /**
     * 是否无限重试（默认关闭，开启后忽略 maxRetries）
     */
    private boolean infiniteRetry = false;
    /**
     * sessionId 变化后是否重试
     */
    private boolean sessionChangeRetry = true;
    /**
     * 自定义每次重试的延迟（毫秒）。如果配置，则优先使用该列表。
     */
    private List<Long> retryDelays;
}
