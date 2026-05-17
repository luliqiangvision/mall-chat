package com.treasurehunt.chat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treasurehunt.chat.context.ChatRequestContext;
import com.treasurehunt.chat.utils.AgentTokenSupport;
import com.treasurehunt.chat.utils.BusinessLineResolver;
import com.treasurehuntshop.mall.common.api.commonUtil.CommonResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 买家 HTTP：从网关透传的 {@code X-Business-Line} 解析业务线。
 * 客服 {@code /chat/agent-service/**}：从登录 JWT 的 extra 读取（登录/identities 仍走请求头）。
 */
@Component
public class ChatRequestContextInterceptor implements HandlerInterceptor {

    private static final String AGENT_CHAT_API_PREFIX = "/chat/agent-service/";

    public static final String HEADER_X_BUSINESS_LINE = "X-Business-Line";

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull Object handler) {
        try {
            String businessLine = resolveBusinessLine(request);
            ChatRequestContext.setBusinessLine(businessLine);
            return true;
        } catch (IllegalArgumentException ex) {
            writeError(response, ex.getMessage());
            return false;
        }
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull Object handler, Exception ex) {
        ChatRequestContext.clear();
    }

    private String resolveBusinessLine(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith(AGENT_CHAT_API_PREFIX)) {
            return AgentTokenSupport.resolveBusinessLineFromHttp(request);
        }
        return BusinessLineResolver.resolve(request.getHeader(HEADER_X_BUSINESS_LINE));
    }

    private void writeError(HttpServletResponse response, String message) {
        try {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(CommonResult.buildError(message)));
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
    }
}
