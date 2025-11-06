package com.treasurehunt.chat.component.manager;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MessageIdGenerateResult {

    private boolean redisAvailable;

    private Long serverMsgId;
}


