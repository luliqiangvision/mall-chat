package com.treasurehunt.chat.utils;

import com.treasurehuntshop.mall.common.enums.BusinessIdentifierEnum;

/**
 * 业务线解析器：统一把外部请求头映射到受控枚举值。
 */
public final class BusinessLineResolver {

    private BusinessLineResolver() {
    }

    public static String resolve(String rawBusinessLine) {
        String candidate = (rawBusinessLine == null || rawBusinessLine.trim().isEmpty())
                ? BusinessIdentifierEnum.TREASURE_HUNT_SHOP.name()
                : rawBusinessLine.trim();
        try {
            return BusinessIdentifierEnum.valueOf(candidate).name();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("非法业务线标识: " + rawBusinessLine);
        }
    }
}

