package com.rcpawn.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate; // 👈 改包名
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

@Component
public class RedisUtil {

    @Autowired
    // 👇 改这里！不要用 ReactiveRedisTemplate<String, String>
    private ReactiveStringRedisTemplate redisTemplate;

    // 定义 Lua 脚本
    // ARGV[1]: 过期时间(秒), ARGV[2]: value
    private static final String SCRIPT_SET_IF_ABSENT =
            "if redis.call('setnx', KEYS[1], ARGV[2]) == 1 then " +
                    "   redis.call('expire', KEYS[1], ARGV[1]); " +
                    "   return 1; " +
                    "else " +
                    "   return 0; " +
                    "end";

    /**
     * 原子操作：不存在则设置并返回 true，存在则返回 false
     */
    public Mono<Boolean> setIfAbsent(String key, String value, long time) {
        RedisScript<Long> script = RedisScript.of(SCRIPT_SET_IF_ABSENT, Long.class);
        // 注意：execute 的第二个参数 keys 是 List，第三个参数 args 也是 List
        // 我们需要把参数包装成 List 传进去
        return redisTemplate.execute(script,
                        Collections.singletonList(key), // KEYS[1]
                        List.of(String.valueOf(time), value) // ARGV[1], ARGV[2] (包装成 List)
                ).next()
                .map(result -> result == 1L);
    }

    /**
     * 存入值，并设置过期时间
     * @param key 键
     * @param value 值
     * @param time 过期时间(秒)
     */
    public Mono<Boolean> set(String key, String value, long time) {
        return redisTemplate.opsForValue() // 👇 这里的变量名也改一下
                .set(key, value, Duration.ofSeconds(time));
    }

    /**
     * 获取值
     */
    public Mono<String> get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 判断 key 是否存在
     */
    public Mono<Boolean> hasKey(String key) {
        return redisTemplate.hasKey(key);
    }
    
    /**
     * 删除 key
     */
    public Mono<Long> del(String key) {
        return redisTemplate.delete(key);
    }
}