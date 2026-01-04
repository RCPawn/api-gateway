package com.rcpawn.controller;

import com.rcpawn.common.util.JwtUtil;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/consumer/auth")
public class AuthController {

    @PostMapping("/login")
    public Map<String, Object> login(@RequestParam String username, @RequestParam String password) {
        Map<String, Object> result = new HashMap<>();
        
        // 1. 模拟数据库校验 (真实场景请查数据库)
        if ("admin".equals(username) && "123456".equals(password)) {
            // 2. 校验通过，生成 Token (假设 UserID 是 8888)
            String token = JwtUtil.generateToken("8888", username);
            
            result.put("code", 200);
            result.put("msg", "登录成功");
            result.put("token", token);
        } else {
            result.put("code", 401);
            result.put("msg", "账号或密码错误");
        }
        return result;
    }
}