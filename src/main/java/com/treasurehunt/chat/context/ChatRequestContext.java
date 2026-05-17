package com.treasurehunt.chat.context;

/**
 * 单次 HTTP 请求内的业务线上下文，由 {@link com.treasurehunt.chat.config.ChatRequestContextInterceptor} 从
 * {@code X-Business-Line} 解析写入（与网关 {@code businessLine} → {@code X-Business-Line} 一致）。
 * <p>须在拦截器 {@link com.treasurehunt.chat.config.ChatRequestContextInterceptor#afterCompletion} 中
 * {@link #clear()}，避免线程池复用导致脏读。
 */
public final class ChatRequestContext {

    private static final ThreadLocal<String> BUSINESS_LINE = new ThreadLocal<>();

    private ChatRequestContext() {
    }

    public static void setBusinessLine(String businessLine) {
        BUSINESS_LINE.set(businessLine);
    }

    public static String getBusinessLine() {
        return BUSINESS_LINE.get();
    }

    public static void clear() {
        BUSINESS_LINE.remove();
    }
}
