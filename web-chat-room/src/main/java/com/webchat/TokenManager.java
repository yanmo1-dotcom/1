package com.webchat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TokenManager {
    // 内存存储 Token -> 用户ID 的映射
    private static final Map<String, Long> TOKEN_STORE = new ConcurrentHashMap<>();

    // 生成新 Token
    public static String createToken(Long userId) {
        String token = UUID.randomUUID().toString();
        TOKEN_STORE.put(token, userId);
        return token;
    }

    // 验证 Token 并返回用户ID
    public static Long verifyToken(String token) {
        if (token == null || !token.startsWith("Bearer ")) return null;
        String actualToken = token.substring(7); // 去掉 "Bearer " 前缀
        return TOKEN_STORE.get(actualToken);
    }
    
    // 删除 Token (登出用)
    public static void removeToken(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            TOKEN_STORE.remove(token.substring(7));
        }
    }
}