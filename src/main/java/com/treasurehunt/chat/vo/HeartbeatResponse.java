package com.treasurehunt.chat.vo;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeartbeatResponse {
    private List<HeartbeatResultItem> results;
    private Long serverTime;
}


