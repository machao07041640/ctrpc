package com.ctrpc.common.cache;

import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Redis 缓存工具，支持 cache-aside 模式。
 */
public class RedisCacheService {

    private final StringRedisTemplate redisTemplate;

    public RedisCacheService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Optional<String> get(String key) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }

    public void set(String key, String value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }

    public String getOrLoad(String key, Duration ttl, Supplier<String> loader) {
        Optional<String> cached = get(key);
        if (cached.isPresent()) {
            return cached.get();
        }
        String value = loader.get();
        if (value != null) {
            set(key, value, ttl);
        }
        return value;
    }
}
