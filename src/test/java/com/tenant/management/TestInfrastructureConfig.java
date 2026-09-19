package com.tenant.management;

import org.mockito.stubbing.Answer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.lang.reflect.Array;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestConfiguration
public class TestInfrastructureConfig {

    @Bean
    @Primary
    JwtDecoder jwtDecoder() {
        return TestJwtSupport.decoder();
    }

    @Bean
    @Primary
    StringRedisTemplate stringRedisTemplate() {
        ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, String> values = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, Long> ttls = new ConcurrentHashMap<>();
        Answer<Object> luaOrDefault = invocation -> {
            if ("execute".equals(invocation.getMethod().getName()) && invocation.getArguments().length >= 2
                    && invocation.getArgument(1) instanceof List<?> keys && !keys.isEmpty()) {
                String key = String.valueOf(keys.get(0));
                long window = 60L;
                if (invocation.getArguments().length >= 3 && invocation.getArgument(2) != null) {
                    Object third = invocation.getArgument(2);
                    if (third.getClass().isArray() && Array.getLength(third) > 0) {
                        window = Long.parseLong(String.valueOf(Array.get(third, 0)));
                    } else {
                        window = Long.parseLong(String.valueOf(third));
                    }
                }
                long n = counters.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
                long ttl = ttls.getOrDefault(key, -1L);
                if (ttl < 0) {
                    ttls.put(key, window);
                    ttl = window;
                }
                return List.of(n, ttl);
            }
            return RETURNS_DEFAULTS.answer(invocation);
        };
        StringRedisTemplate template = mock(StringRedisTemplate.class, luaOrDefault);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return counters.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
        });
        when(ops.get(anyString())).thenAnswer(invocation -> values.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            values.put(key, invocation.getArgument(1));
            ttls.put(key, invocation.getArgument(2, Duration.class).getSeconds());
            return null;
        }).when(ops).set(anyString(), anyString(), any(Duration.class));
        when(template.expire(anyString(), any(Duration.class))).thenAnswer(invocation -> {
            ttls.put(invocation.getArgument(0), invocation.getArgument(1, Duration.class).getSeconds());
            return true;
        });
        when(template.getExpire(anyString())).thenAnswer(invocation -> ttls.getOrDefault(invocation.getArgument(0), -2L));
        when(template.delete(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            ttls.remove(key);
            return values.remove(key) != null;
        });
        return template;
    }
}
