package com.treasurehunt.chat.framework.core.websocket.distributed.delivery;

import lombok.Data;

/**
 * 实例信息
 * 
 * @author gaga
 * @since 2025-10-06
 */
@Data
public class InstanceInfo {
    private String instanceId;
    private String ip;
    private int port;
    
    public InstanceInfo() {}
    
    public InstanceInfo(String instanceId, String ip, int port) {
        this.instanceId = instanceId;
        this.ip = ip;
        this.port = port;
    }
}
