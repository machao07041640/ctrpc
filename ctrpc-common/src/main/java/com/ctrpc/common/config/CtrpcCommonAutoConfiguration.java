package com.ctrpc.common.config;

import com.ctrpc.common.cache.RedisCacheService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration
@EnableConfigurationProperties({RedisProperties.class, DataSourceProperties.class})
public class CtrpcCommonAutoConfiguration {

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    public RedisCacheService redisCacheService(StringRedisTemplate redisTemplate) {
        return new RedisCacheService(redisTemplate);
    }
}
