package com.treasurehunt.chat.enums;

/**
 * {@code chat_agent.agent_type} 与库表取值一致。
 */
public enum AgentTypeEnum {

    PRE_SALES("pre-sales", "售前客服"),
    AFTER_SALES("after-sales", "售后客服"),
    /** 公司级：无店铺进线兜底（法务、税务等）；有 shopId 时池空不回退 corporate */
    CORPORATE("corporate", "公司级客服"),
    SYSTEM("system", "系统客服");

    private final String code;
    private final String description;

    AgentTypeEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static AgentTypeEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (AgentTypeEnum t : values()) {
            if (t.code.equals(code)) {
                return t;
            }
        }
        return null;
    }
}
