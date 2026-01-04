package com.rcpawn.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class JwtUtil {
    // 秘钥 (生产环境应该写在配置文件里)
    private static final String SECRET_KEY = "MySecretKey_rcpawn_gateway_demo_2026"; 
    // 过期时间 (24小时)
    private static final long EXPIRATION_TIME = 86400000L; 

    // 1. 生成 Token
    public static String generateToken(String userId, String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", username);
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userId) // 把 UserID 存进 Subject
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(SignatureAlgorithm.HS512, SECRET_KEY)
                .compact();
    }

    // 2. 解析 Token 获取 Claims
    public static Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .setSigningKey(SECRET_KEY)
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            return null; // 解析失败（过期或篡改）
        }
    }

    // 3. 校验并获取 UserID
    public static String getUserId(String token) {
        Claims claims = parseToken(token);
        return claims != null ? claims.getSubject() : null;
    }
}