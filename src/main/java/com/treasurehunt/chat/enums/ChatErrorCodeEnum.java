package com.treasurehunt.chat.enums;

import com.treasurehuntshop.mall.common.enums.BaseAbstactErrorCodeEnum;
import com.treasurehuntshop.mall.common.enums.MicroserviceModuleErrorPrefixEnum;

/**
 * Chat 模块业务错误码。
 * 规则：前 2 位微服务前缀 + 中 2 位子模块 + 末 2 位具体错误。
 */
public enum ChatErrorCodeEnum implements BaseAbstactErrorCodeEnum {

    CHAT_BATCH_QUERY_MSG_ID_FAILED(buildErrorCode("01", "01"), "批量查询会话最新消息ID失败");

    private static final String CHAT_PREFIX = MicroserviceModuleErrorPrefixEnum.MESSAGE_CENTER_CHAT.getFourDigitPrefix();

    private final String errorCode;
    private final String errorMsg;

    ChatErrorCodeEnum(String errorCode, String errorMsg) {
        this.errorCode = errorCode;
        this.errorMsg = errorMsg;
    }

    private static String buildErrorCode(String moduleCode, String detailCode) {
        return CHAT_PREFIX + moduleCode + detailCode;
    }

    @Override
    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public String getErrorMsg() {
        return errorMsg;
    }
}
