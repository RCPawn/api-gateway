package com.rcpawn;

import com.rcpawn.common.util.JwtUtil;

public class TestJwt {
    public static void main(String[] args) {
        // 模拟给用户 ID=888, Name=Admin 生成令牌
        String token = JwtUtil.generateToken("888", "Admin");
        System.out.println("请复制这个 Token 去测试:");
        System.out.println(token);
    }
}
