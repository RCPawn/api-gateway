package com.rcpawn.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate; // 👈 改包名
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.time.Duration;

@Component
public class RedisUtil {

    @Autowired
    // 👇 改这里！不要用 ReactiveRedisTemplate<String, String>
    private ReactiveStringRedisTemplate redisTemplate;

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