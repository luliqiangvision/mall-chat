package com.treasurehunt.chat.po;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IdempotencyCheckResult {

    private boolean duplicateFound;

    private Long serverMsgId;

    private boolean usedRedis;
}


