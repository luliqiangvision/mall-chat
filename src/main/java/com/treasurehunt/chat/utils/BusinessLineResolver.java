package com.treasurehunt.chat.utils;

import com.treasurehuntshop.mall.common.enums.BusinessIdentifierEnum;

/**
 * 业务线解析器：统一把外部请求头映射到受控枚举值。
 */
public final class BusinessLineResolver {

    private BusinessLineResolver() {
    }

    public static String resolve(String rawBusinessLine) {
        if (rawBusinessLine == null || rawBusinessLine.trim().isEmpty()) {
            throw new IllegalArgumentException("缺少业务线（买家须由网关透传 X-Business-Line；登录/identities 须传 businessLine 头）");
        }
        String candidate = rawBusinessLine.trim();
        try {
            return BusinessIdentifierEnum.valueOf(candidate).name();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("非法业务线标识: " + rawBusinessLine);
        }
    }
}

