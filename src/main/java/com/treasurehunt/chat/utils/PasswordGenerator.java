package com.treasurehunt.chat.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码生成工具类
 * 用于生成BCrypt加密后的密码哈希值
 */
public class PasswordGenerator {
    
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        
        // 生成密码"123456"的BCrypt哈希值
        String password = "123456";
        String encodedPassword = encoder.encode(password);
        
        System.out.println("原始密码: " + password);
        System.out.println("BCrypt哈希值: " + encodedPassword);
        System.out.println();
        
        // 验证生成的哈希值
        boolean matches = encoder.matches(password, encodedPassword);
        System.out.println("验证结果: " + matches);
        
        // 生成多个不同的哈希值（BCrypt每次生成的哈希值都不同）
        System.out.println("\n=== 多个不同的哈希值（都对应密码123456）===");
        for (int i = 0; i < 3; i++) {
            String hash = encoder.encode(password);
            boolean verified = encoder.matches(password, hash);
            System.out.println("哈希值 " + (i + 1) + ": " + hash + " (验证: " + verified + ")");
        }
    }
}

