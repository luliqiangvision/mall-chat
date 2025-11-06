package com.treasurehunt.chat.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treasurehunt.chat.vo.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * WebSocket安全过滤器,注意,这个不是文件扫描病毒的组件,那个组件是VirusScanner
 * 用于过滤恶意内容、病毒链接、脚本注入等安全威胁
 */
@Slf4j
@Component
public class WebSocketSecurityFilter {

    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 恶意脚本模式
    private static final List<Pattern> SCRIPT_PATTERNS = Arrays.asList(
        Pattern.compile("<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
        Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE),
        Pattern.compile("on\\w+\\s*=", Pattern.CASE_INSENSITIVE),
        Pattern.compile("eval\\s*\\(", Pattern.CASE_INSENSITIVE),
        Pattern.compile("expression\\s*\\(", Pattern.CASE_INSENSITIVE)
    );
    
    // 恶意链接模式
    private static final List<Pattern> MALICIOUS_URL_PATTERNS = Arrays.asList(
        Pattern.compile("(https?://)?(www\\.)?(bit\\.ly|tinyurl|t\\.co|goo\\.gl|short\\.link)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(https?://)?(www\\.)?[a-zA-Z0-9.-]*\\.(exe|bat|cmd|scr|pif|com|vbs|js|jar|zip|rar|7z)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(https?://)?(www\\.)?[a-zA-Z0-9.-]*\\.(onion|tor)", Pattern.CASE_INSENSITIVE)
    );
    
    // 最大消息长度限制
    private static final int MAX_MESSAGE_LENGTH = 10000;
    
    
    /**
     * 验证消息内容安全性
     */
    public SecurityCheckResult validateMessage(String messageContent) {
        if (messageContent == null || messageContent.trim().isEmpty()) {
            return SecurityCheckResult.valid();
        }
        
        // 检查消息长度
        if (messageContent.length() > MAX_MESSAGE_LENGTH) {
            log.warn("消息长度超限: {}", messageContent.length());
            return SecurityCheckResult.invalid("消息内容过长");
        }
        
        // 检查恶意脚本
        for (Pattern pattern : SCRIPT_PATTERNS) {
            if (pattern.matcher(messageContent).find()) {
                log.warn("检测到恶意脚本: {}", messageContent);
                return SecurityCheckResult.invalid("消息包含恶意脚本");
            }
        }
        
        // 检查恶意链接
        for (Pattern pattern : MALICIOUS_URL_PATTERNS) {
            if (pattern.matcher(messageContent).find()) {
                log.warn("检测到可疑链接: {}", messageContent);
                return SecurityCheckResult.invalid("消息包含可疑链接");
            }
        }
        return SecurityCheckResult.valid();
    }
    
    /**
     * 验证ChatMessage对象
     */
    public SecurityCheckResult validateChatMessage(ChatMessage chatMessage) {
        if (chatMessage == null) {
            return SecurityCheckResult.invalid("消息对象为空");
        }
        // 验证用户ID格式
        if (chatMessage.getFromUserId() == null || chatMessage.getFromUserId().trim().isEmpty()) {
            return SecurityCheckResult.invalid("发送者ID不能为空");
        }
        // 验证用户ID格式（只允许数字和字母）
        if (!chatMessage.getFromUserId().matches("^[a-zA-Z0-9]+$")) {
            return SecurityCheckResult.invalid("发送者ID格式不正确");
        }
        // 验证消息内容
        SecurityCheckResult contentResult = validateMessage(chatMessage.getContent());
        if (!contentResult.isValid()) {
            return contentResult;
        }

        return SecurityCheckResult.valid();
    }
    
    /**
     * 过滤消息内容
     */
    public String filterMessage(String messageContent) {
        if (messageContent == null) {
            return "";
        }
        String filtered = messageContent;
        // 移除HTML标签
        filtered = filtered.replaceAll("<[^>]+>", "");
        // 移除特殊字符
        filtered = filtered.replaceAll("[<>\"'&]", "");
        // 限制长度
        if (filtered.length() > MAX_MESSAGE_LENGTH) {
            filtered = filtered.substring(0, MAX_MESSAGE_LENGTH);
        }
        return filtered.trim();
    }
    
    /**
     * 发送安全错误消息
     */
    public void sendSecurityErrorMessage(WebSocketSession session, String reason) {
        try {
            ChatMessage errorMessage = new ChatMessage("security_error", "消息被安全系统拦截: " + reason);
            errorMessage.setTimestamp(new java.util.Date());
            String jsonMessage = objectMapper.writeValueAsString(errorMessage);
            session.sendMessage(new TextMessage(jsonMessage));
            log.warn("发送安全错误消息给用户: {}, 原因: {}", session.getId(), reason);
        } catch (IOException e) {
            log.error("发送安全错误消息失败", e);
        }
    }
    
    /**
     * 安全检查结果
     */
    public static class SecurityCheckResult {
        private final boolean valid;
        private final String reason;
        private SecurityCheckResult(boolean valid, String reason) {
            this.valid = valid;
            this.reason = reason;
        }
        public static SecurityCheckResult valid() {
            return new SecurityCheckResult(true, null);
        }
        public static SecurityCheckResult invalid(String reason) {
            return new SecurityCheckResult(false, reason);
        }
        public boolean isValid() {
            return valid;
        }
        
        public String getReason() {
            return reason;
        }
    }
}
