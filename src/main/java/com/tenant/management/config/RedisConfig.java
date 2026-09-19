package com.tenant.management.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Set;

@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    public LettuceClientConfigurationBuilderCustomizer redisCommandTimeout(
            @Value("${spring.data.redis.timeout:2s}") Duration timeout
    ) {
        return builder -> builder.commandTimeout(timeout);
    }

    @Bean
    @Primary
    @ConditionalOnBean(RedisConnectionFactory.class)
    public RedisCacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            @Value("${app.analytics-cache-ttl-seconds:60}") long ttlSeconds
    ) {
        RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(ttlSeconds))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new GenericJackson2JsonRedisSerializer()));
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(configuration)
                .initialCacheNames(Set.of("analytics"))
                .build();
    }
}
