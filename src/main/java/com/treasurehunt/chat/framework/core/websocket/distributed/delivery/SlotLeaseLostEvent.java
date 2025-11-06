package com.treasurehunt.chat.framework.core.websocket.distributed.delivery;

import org.springframework.context.ApplicationEvent;

/**
 * 槽位租约丢失事件（用于通知消费者需要重新初始化并抢占槽位）
 */
public class SlotLeaseLostEvent extends ApplicationEvent {

    private final String serviceName;
    private final Integer slotId;

    public SlotLeaseLostEvent(Object source, String serviceName, Integer slotId) {
        super(source);
        this.serviceName = serviceName;
        this.slotId = slotId;
    }

    public String getServiceName() { return serviceName; }

    public Integer getSlotId() { return slotId; }
}


