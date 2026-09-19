package com.tenant.management.service;

import com.tenant.management.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimitServiceTest {

    @Test
    void throws429WhenRedisCounterExceedsLimit() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any())).thenReturn(List.of(6L, 30L));

        RateLimitService service = new RateLimitService(redis);
        ReflectionTestUtils.setField(service, "maxRequests", 5);
        ReflectionTestUtils.setField(service, "windowSeconds", 60);

        assertThatThrownBy(() -> service.check("1:1:POST:/tasks", new MockHttpServletResponse()))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void failsClosedWhenRedisIsDown() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any())).thenThrow(new RuntimeException("connection refused"));
        RateLimitService service = new RateLimitService(redis);
        ReflectionTestUtils.setField(service, "maxRequests", 5);
        ReflectionTestUtils.setField(service, "windowSeconds", 60);
        assertThatThrownBy(() -> service.check("1:1:POST:/tasks", new MockHttpServletResponse()))
                .isInstanceOf(com.tenant.management.exception.ServiceUnavailableException.class);
    }
}
