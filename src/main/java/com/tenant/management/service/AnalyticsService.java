package com.tenant.management.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenant.management.dto.AnalyticsResponse;
import com.tenant.management.dto.TrendPoint;
import com.tenant.management.entity.TaskPriority;
import com.tenant.management.entity.TaskStatus;
import com.tenant.management.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    public static final String CACHE_KEY_PREFIX = "analytics::org:";
    static final Clock CLOCK = Clock.systemUTC();
    private static final int TREND_DAYS = 14;

    private final TaskRepository taskRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AccessService accessService;

    @Value("${app.analytics-cache-ttl-seconds:60}")
    private long ttlSeconds;

    @Transactional(readOnly = true)
    public AnalyticsResponse dashboard() {
        Long orgId = accessService.currentOrganizationId();
        AnalyticsResponse hit = cacheGet(orgId);
        if (hit != null) {
            return hit;
        }
        AnalyticsResponse computed = compute(orgId);
        cachePut(orgId, computed);
        return computed;
    }

    public void evict(Long orgId) {
        try {
            redisTemplate.delete(cacheKey(orgId));
        } catch (RuntimeException ex) {
            log.warn("Analytics cache evict failed for org {}: {}", orgId, ex.getMessage());
        }
    }

    private AnalyticsResponse cacheGet(Long orgId) {
        try {
            String json = redisTemplate.opsForValue().get(cacheKey(orgId));
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, AnalyticsResponse.class);
        } catch (RuntimeException | JsonProcessingException ex) {
            log.warn("Analytics cache read failed: {}", ex.getMessage());
            return null;
        }
    }

    private void cachePut(Long orgId, AnalyticsResponse value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(cacheKey(orgId), json, Duration.ofSeconds(ttlSeconds));
        } catch (RuntimeException | JsonProcessingException ex) {
            log.warn("Analytics cache write failed: {}", ex.getMessage());
        }
    }

    private AnalyticsResponse compute(Long orgId) {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (TaskStatus status : TaskStatus.values()) {
            byStatus.put(status.name(), 0L);
        }
        taskRepository.countByStatus(orgId).forEach(row -> byStatus.put(row[0].toString(), (Long) row[1]));

        Map<String, Long> byPriority = new LinkedHashMap<>();
        for (TaskPriority priority : TaskPriority.values()) {
            byPriority.put(priority.name(), 0L);
        }
        taskRepository.countByPriority(orgId).forEach(row -> byPriority.put(row[0].toString(), (Long) row[1]));

        long total = taskRepository.countByOrganizationId(orgId);
        long done = taskRepository.countByOrganizationIdAndStatus(orgId, TaskStatus.DONE);
        long pending = taskRepository.countByOrganizationIdAndStatusIn(
                orgId, List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS));
        LocalDate today = LocalDate.now(CLOCK);
        long overdue = taskRepository.countOverdue(orgId, today, TaskStatus.DONE);
        double rate = total == 0 ? 0.0 : (done * 100.0) / total;

        LocalDateTime from = today.minusDays(TREND_DAYS - 1L).atStartOfDay();
        return AnalyticsResponse.builder()
                .totalTasks(total)
                .completedTasks(done)
                .pendingTasks(pending)
                .completionRate(Math.round(rate * 100.0) / 100.0)
                .overdueTasks(overdue)
                .tasksByStatus(byStatus)
                .tasksByPriority(byPriority)
                .createdTrend(toTrend(taskRepository.countCreatedByDay(orgId, from), today))
                .completedTrend(toTrend(taskRepository.countCompletedByDay(orgId, from), today))
                .build();
    }

    private List<TrendPoint> toTrend(List<Object[]> rows, LocalDate today) {
        Map<String, Long> byDate = new LinkedHashMap<>();
        for (int i = TREND_DAYS - 1; i >= 0; i--) {
            byDate.put(today.minusDays(i).toString(), 0L);
        }
        for (Object[] row : rows) {
            LocalDate date = toLocalDate(row[0]);
            if (date == null) {
                continue;
            }
            String key = date.toString();
            if (byDate.containsKey(key)) {
                byDate.put(key, ((Number) row[1]).longValue());
            }
        }
        List<TrendPoint> points = new ArrayList<>();
        byDate.forEach((date, count) -> points.add(TrendPoint.builder().date(date).count(count).build()));
        return points;
    }

    private LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof Date date) {
            return date.toLocalDate();
        }
        return LocalDate.parse(value.toString());
    }

    public static String cacheKey(Long orgId) {
        return CACHE_KEY_PREFIX + orgId;
    }
}
