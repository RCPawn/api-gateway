package com.rcpawn.common.util;

public class UserContext {
    // ThreadLocal 保证线程隔离，每个请求互不干扰
    private static final ThreadLocal<String> userHolder = new ThreadLocal<>();

    public static void setUserId(String userId) {
        userHolder.set(userId);
    }

    public static String getUserId() {
        return userHolder.get();
    }

    // 务必记得清除，防止内存泄漏（尤其是在线程池环境下）
    public static void remove() {
        userHolder.remove();
    }
}