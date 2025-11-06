package com.treasurehunt.chat.vo;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeartbeatResultItem {
    private String conversationId;
    private boolean needPull;
    private Long latestServerMsgId;
    private Long pullFrom;
}


