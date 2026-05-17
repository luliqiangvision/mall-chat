package com.treasurehunt.chat.utils;

import cn.dev33.satoken.exception.SaTokenException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * 客服 JWT：解析 token 中的 loginId 与 businessLine（WebSocket 无 Servlet 上下文，不能调用 setTokenValue）。
 */
public final class AgentTokenSupport {

    private static final String QUERY_TOKEN = "token";
    private static final String EXTRA_BUSINESS_LINE = "businessLine";

    private AgentTokenSupport() {
    }

    public static final class AgentAuthContext {
        private final String agentId;
        private final String businessLine;

        public AgentAuthContext(String agentId, String businessLine) {
            this.agentId = agentId;
            this.businessLine = businessLine;
        }

        public String getAgentId() {
            return agentId;
        }

        public String getBusinessLine() {
            return businessLine;
        }
    }

    public static AgentAuthContext parseAgentToken(String tokenValue) {
        if (!StringUtils.hasText(tokenValue)) {
            throw new IllegalArgumentException("缺少客服登录 token");
        }
        String token = tokenValue.trim();
        try {
            Object loginId = StpUtilForType.getLoginIdByToken(StpUtilForType.TYPE_AGENT_LOGIN, token);
            if (loginId == null || !StringUtils.hasText(loginId.toString())) {
                throw new IllegalArgumentException("token 无效");
            }
            Object businessLineObj = StpUtilForType.getExtra(
                    StpUtilForType.TYPE_AGENT_LOGIN, token, EXTRA_BUSINESS_LINE);
            if (businessLineObj == null || !StringUtils.hasText(businessLineObj.toString())) {
                throw new IllegalArgumentException("token 中缺少业务线，请重新登录");
            }
            String businessLine = BusinessLineResolver.resolve(businessLineObj.toString());
            return new AgentAuthContext(loginId.toString().trim(), businessLine);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (SaTokenException ex) {
            throw new IllegalArgumentException("token 无效或已过期: " + ex.getMessage());
        }
    }

    public static AgentAuthContext authenticateWebSocket(WebSocketSession session) {
        return parseAgentToken(extractTokenFromWebSocket(session));
    }

    public static String resolveBusinessLineFromHttp(HttpServletRequest request) {
        String token = extractTokenFromAuthorization(request.getHeader(HttpHeaders.AUTHORIZATION));
        return parseAgentToken(token).getBusinessLine();
    }

    public static String extractTokenFromWebSocket(WebSocketSession session) {
        if (session == null) {
            return null;
        }
        String fromHeader = extractTokenFromAuthorization(
                session.getHandshakeHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        if (StringUtils.hasText(fromHeader)) {
            return fromHeader;
        }
        URI uri = session.getUri();
        if (uri == null || !StringUtils.hasText(uri.getQuery())) {
            return null;
        }
        for (String param : uri.getQuery().split("&")) {
            if (param.startsWith(QUERY_TOKEN + "=")) {
                String raw = param.substring(QUERY_TOKEN.length() + 1);
                return URLDecoder.decode(raw, StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static String extractTokenFromAuthorization(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            return null;
        }
        String value = authorization.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return value.substring(7).trim();
        }
        return value;
    }
}
