package com.rcpawn.common.util;


public class UserContext {
    // 存放 UserID
    private static final ThreadLocal<String> userIdHolder = new ThreadLocal<>();
    // 【新增】存放 Token (因为 Feign 调用需要把这个令牌原样传下去)
    private static final ThreadLocal<String> tokenHolder = new ThreadLocal<>();

    public static void setUserId(String userId) {
        userIdHolder.set(userId);
    }

    public static String getUserId() {
        return userIdHolder.get();
    }

    // 【新增】设置 Token
    public static void setToken(String token) {
        tokenHolder.set(token);
    }

    // 【新增】获取 Token
    public static String getToken() {
        return tokenHolder.get();
    }

    // 清除所有（防止内存泄漏）
    public static void remove() {
        userIdHolder.remove();
        tokenHolder.remove();
    }
}