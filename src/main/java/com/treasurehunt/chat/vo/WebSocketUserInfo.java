package com.treasurehunt.chat.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

/**
 * WebSocket用户信息
 * 只包含会话所需字段，避免在WebSocket中直接使用数据库实体类
 */
@Schema(description = "WebSocket用户信息")
public class WebSocketUserInfo implements Serializable {
    
    @Schema(description = "用户ID")
    private String userId;
    
    @Schema(description = "用户类型：user-客户，customer-客服")
    private String userType;
    
    @Schema(description = "用户名")
    private String username;
    
    @Schema(description = "昵称")
    private String nickname;
    
    @Schema(description = "头像")
    private String icon;
    
    private static final long serialVersionUID = 1L;
    
    public WebSocketUserInfo() {}
    
    public WebSocketUserInfo(String userId, String userType) {
        this.userId = userId;
        this.userType = userType;
    }
    
    public WebSocketUserInfo(String userId, String userType, String username) {
        this.userId = userId;
        this.userType = userType;
        this.username = username;
    }
    
    // Getters and Setters
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getUserType() {
        return userType;
    }
    
    public void setUserType(String userType) {
        this.userType = userType;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getNickname() {
        return nickname;
    }
    
    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
    
    public String getIcon() {
        return icon;
    }
    
    public void setIcon(String icon) {
        this.icon = icon;
    }
    
    @Override
    public String toString() {
        return "WebSocketUserInfo{" +
                "userId='" + userId + '\'' +
                ", userType='" + userType + '\'' +
                ", username='" + username + '\'' +
                ", nickname='" + nickname + '\'' +
                ", icon='" + icon + '\'' +
                '}';
    }
}
