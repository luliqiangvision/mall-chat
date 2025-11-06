package com.treasurehunt.chat.framework.core.websocket.distributed.delivery;

import com.treasurehunt.chat.framework.core.websocket.distributed.spi.InstanceRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Nacos 服务发现
 * 
 * @author gaga
 * @since 2025-10-06
 */
@Slf4j
@Component("customNacosServiceDiscovery")
public class NacosServiceDiscovery {

    @Autowired
    private InstanceRegistry instanceRegistry;

    /**
     * 获取实例信息
     * @param instanceId 实例ID
     * @return 实例信息
     */
    public InstanceInfo getInstance(String instanceId) {
        try {
            String instanceInfo = instanceRegistry.getInstanceInfo(instanceId);
            if (instanceInfo != null && instanceInfo.contains(":")) {
                String[] parts = instanceInfo.split(":");
                InstanceInfo info = new InstanceInfo();
                info.setInstanceId(instanceId);
                info.setIp(parts[0]);
                info.setPort(Integer.parseInt(parts[1]));
                return info;
            }
        } catch (Exception e) {
            log.warn("Failed to get instance info for: {}", instanceId, e);
        }
        return null;
    }

    /**
     * 检查实例是否活跃
     * @param instanceId 实例ID
     * @return 是否活跃
     */
    public boolean isInstanceActive(String instanceId) {
        return instanceRegistry.isInstanceActive(instanceId);
    }

    /**
     * 根据地址检查实例是否活跃
     * @param ip 实例IP
     * @param port 实例端口
     * @return 是否活跃
     */
    public boolean isInstanceActiveByAddress(String ip, int port) {
        try {
            java.util.Set<String> activeInstances = instanceRegistry.getActiveInstances();
            for (String instanceId : activeInstances) {
                InstanceInfo instance = getInstance(instanceId);
                if (instance != null && ip.equals(instance.getIp()) && port == instance.getPort()) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to check instance active by address: {}:{}", ip, port, e);
        }
        return false;
    }

    /**
     * 根据地址获取实例ID
     * @param instanceAddress 实例地址 (IP:Port)
     * @return 实例ID
     */
    public String getInstanceIdByAddress(String instanceAddress) {
        try {
            String[] parts = instanceAddress.split(":");
            if (parts.length == 2) {
                String ip = parts[0];
                int port = Integer.parseInt(parts[1]);
                
                java.util.Set<String> activeInstances = instanceRegistry.getActiveInstances();
                for (String instanceId : activeInstances) {
                    InstanceInfo instance = getInstance(instanceId);
                    if (instance != null && ip.equals(instance.getIp()) && port == instance.getPort()) {
                        return instanceId;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get instance ID by address: {}", instanceAddress, e);
        }
        return null;
    }
}