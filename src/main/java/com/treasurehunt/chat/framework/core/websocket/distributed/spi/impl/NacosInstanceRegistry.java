package com.treasurehunt.chat.framework.core.websocket.distributed.spi.impl;

import com.alibaba.cloud.nacos.NacosServiceManager;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.treasurehunt.chat.framework.core.websocket.distributed.spi.InstanceRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.event.InstanceRegisteredEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nacos实例注册实现
 * 使用Nacos进行服务实例的注册和发现
 */
@Component
public class NacosInstanceRegistry implements InstanceRegistry {
    
    @Value("${spring.application.name}")
    private String serviceName;
    
    @Value("${websocket.instance.ip}")
    private String instanceIp;
    
    @Value("${websocket.instance.port}")
    private int instancePort;
    
    @Autowired
    private NacosServiceManager nacosServiceManager;
    
    @Autowired(required = false)
    private DiscoveryClient discoveryClient;
    
    private NamingService namingService;
    private String currentInstanceId;
    private final Set<String> activeInstances = ConcurrentHashMap.newKeySet();
    
    @EventListener(InstanceRegisteredEvent.class)
    @Order(0)
    public void onReady() throws Exception {
        // 通过 NacosServiceManager 获取 NamingService（复用自动配置内的实例）
        try {
            this.namingService = nacosServiceManager.getNamingService();
        } catch (Throwable ignore) {
            this.namingService = null;
        }

        // 计算并缓存当前实例ID
        if (namingService != null) {
            List<Instance> instances = namingService.getAllInstances(serviceName);
            for (Instance instance : instances) {
                if (instanceIp.equals(instance.getIp()) && instancePort == instance.getPort()) {
                    currentInstanceId = instance.getInstanceId();
                    break;
                }
            }
        }

        // 如果未获取到，则尝试通过 DiscoveryClient 回退
        if (currentInstanceId == null && discoveryClient != null) {
            List<ServiceInstance> list = discoveryClient.getInstances(serviceName);
            for (ServiceInstance si : list) {
                if (instanceIp.equals(si.getHost()) && instancePort == si.getPort()) {
                    String id = si.getInstanceId();
                    if (id == null) {
                        id = serviceName + "-" + instanceIp + ":" + instancePort;
                    }
                    currentInstanceId = id;
                    break;
                }
            }
        }

        // 刷新活跃实例列表
        refreshActiveInstances();
    }
    
    @Override
    public void registerInstance(String instanceId, String instanceInfo) {
        // 统一到 Spring Cloud 的自动注册，这里仅维护缓存
        refreshActiveInstances();
    }
    
    @Override
    public void unregisterInstance(String instanceId) {
        // 实例注销在destroy()方法中完成
        refreshActiveInstances();
    }
    
    @Override
    public Set<String> getActiveInstances() {
        return new HashSet<>(activeInstances);
    }
    
    @Override
    public String getCurrentInstanceId() {
        return currentInstanceId;
    }
    
    @Override
    public String getInstanceInfo(String instanceId) {
        try {
            List<Instance> instances = namingService.getAllInstances(serviceName);
            for (Instance instance : instances) {
                if (instanceId.equals(instance.getInstanceId())) {
                    return instance.getIp() + ":" + instance.getPort();
                }
            }
        } catch (Exception e) {
            // 记录日志
        }
        return null;
    }
    
    @Override
    public boolean isInstanceActive(String instanceId) {
        return activeInstances.contains(instanceId);
    }
    
    @Override
    public int getActiveInstanceCount() {
        return activeInstances.size();
    }
    
    /**
     * 刷新活跃实例列表
     */
    private void refreshActiveInstances() {
        try {
            activeInstances.clear();
            if (namingService != null) {
                List<Instance> instances = namingService.getAllInstances(serviceName);
                for (Instance instance : instances) {
                    if (instance.isHealthy() && instance.isEnabled()) {
                        activeInstances.add(instance.getInstanceId());
                    }
                }
                return;
            }
            if (discoveryClient != null) {
                List<ServiceInstance> list = discoveryClient.getInstances(serviceName);
                for (ServiceInstance si : list) {
                    String id = si.getInstanceId();
                    if (id == null) {
                        id = serviceName + "-" + si.getHost() + ":" + si.getPort();
                    }
                    activeInstances.add(id);
                }
            }
        } catch (Exception e) {
            // 记录日志
        }
    }
}
