package com.treasurehunt.chat.websocket;

import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话管理器
 * 统一管理客户和客服的WebSocket会话
 */
public class SessionManager {
    
    // 客户会话存储
    private static final Map<String, WebSocketSession> customerSessions = new ConcurrentHashMap<>();
    
    // 客服会话存储
    private static final Map<String, WebSocketSession> customerServiceSessions = new ConcurrentHashMap<>();
    
    /**
     * 添加客户会话
     */
    public static void addCustomerSession(String userId, WebSocketSession session) {
        customerSessions.put(userId, session);
    }
    
    /**
     * 添加客服会话
     */
    public static void addCustomerServiceSession(String userId, WebSocketSession session) {
        customerServiceSessions.put(userId, session);
    }
    
    /**
     * 获取客户会话
     */
    public static WebSocketSession getCustomerSession(String userId) {
        return customerSessions.get(userId);
    }
    
    /**
     * 获取客服会话
     */
    public static WebSocketSession getCustomerServiceSession(String userId) {
        return customerServiceSessions.get(userId);
    }
    
    /**
     * 移除客户会话
     */
    public static void removeCustomerSession(WebSocketSession session) {
        customerSessions.entrySet().removeIf(entry -> entry.getValue().equals(session));
    }
    
    /**
     * 移除客服会话
     */
    public static void removeCustomerServiceSession(WebSocketSession session) {
        customerServiceSessions.entrySet().removeIf(entry -> entry.getValue().equals(session));
    }
    
    /**
     * 获取所有客户会话
     */
    public static Map<String, WebSocketSession> getCustomerSessions() {
        return customerSessions;
    }
    
    /**
     * 获取所有客服会话
     */
    public static Map<String, WebSocketSession> getCustomerServiceSessions() {
        return customerServiceSessions;
    }
    
    /**
     * 获取在线客户数量
     */
    public static int getCustomerCount() {
        return customerSessions.size();
    }
    
    /**
     * 获取在线客服数量
     */
    public static int getCustomerServiceCount() {
        return customerServiceSessions.size();
    }
}
