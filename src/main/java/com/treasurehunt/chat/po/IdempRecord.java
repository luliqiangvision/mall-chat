package com.treasurehunt.chat.po;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 幂等性记录（Redis中存储的JSON结构）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdempRecord {
    
    /**
     * 状态：PENDING（处理中）或 DONE（已完成）
     */
    @JsonProperty("status")
    private String status;
    
    /**
     * 结果值（DONE状态下存储 serverMsgId，PENDING 时为空）
     */
    @JsonProperty("result")
    private String result;
    
    /**
     * 时间戳（毫秒）
     */
    @JsonProperty("ts")
    private Long ts;
    
    /**
     * 占坑者标识（用于排障，格式：host:pid:traceId）
     */
    @JsonProperty("owner")
    private String owner;
    
    /**
     * 创建 PENDING 状态的记录
     */
    public static IdempRecord pending(String owner) {
        return new IdempRecord("PENDING", null, System.currentTimeMillis(), owner);
    }
    
    /**
     * 创建 DONE 状态的记录
     */
    public static IdempRecord done(Long serverMsgId, String owner) {
        return new IdempRecord("DONE", String.valueOf(serverMsgId), System.currentTimeMillis(), owner);
    }
    
    /**
     * 判断是否为数字结果（用于快速判断是否是DONE状态的有效结果）
     */
    public boolean isNumericResult() {
        if (result == null || result.isEmpty()) {
            return false;
        }
        try {
            Long.parseLong(result);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}

