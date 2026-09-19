package com.tenant.management.service;

import com.tenant.management.exception.RateLimitExceededException;
import com.tenant.management.exception.ServiceUnavailableException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    static final DefaultRedisScript<List> INCR_EXPIRE = new DefaultRedisScript<>();

    static {
        INCR_EXPIRE.setResultType(List.class);
        INCR_EXPIRE.setScriptText("""
                local n = redis.call('INCR', KEYS[1])
                local ttl = redis.call('TTL', KEYS[1])
                if ttl < 0 then
                  redis.call('EXPIRE', KEYS[1], ARGV[1])
                  ttl = tonumber(ARGV[1])
                end
                return {n, ttl}
                """);
    }

    private final StringRedisTemplate redisTemplate;

    @Value("${app.rate-limit.requests:30}")
    private int maxRequests;

    @Value("${app.rate-limit.window-seconds:60}")
    private long windowSeconds;

    public void check(String key, HttpServletResponse response) {
        String redisKey = "ratelimit:" + key;
        long count;
        try {
            List<?> result = redisTemplate.execute(INCR_EXPIRE, List.of(redisKey), String.valueOf(windowSeconds));
            if (result == null || result.isEmpty() || result.get(0) == null) {
                throw new IllegalStateException("Redis rate limiter returned no counter");
            }
            count = ((Number) result.get(0)).longValue();
        } catch (RateLimitExceededException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Redis rate limiter unavailable: {}", ex.getMessage());
            throw new ServiceUnavailableException("Rate limiting is unavailable. Try again later.");
        }
        long remaining = Math.max(0, maxRequests - count);
        response.setHeader("X-RateLimit-Limit", String.valueOf(maxRequests));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        if (count > maxRequests) {
            response.setHeader("Retry-After", String.valueOf(windowSeconds));
            throw new RateLimitExceededException("Too many write requests. Try again later.");
        }
    }
}
