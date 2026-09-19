package com.tenant.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsResponse implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private long totalTasks;
    private long completedTasks;
    /** Pending = TODO + IN_PROGRESS (not DONE). */
    private long pendingTasks;
    private double completionRate;
    /** Overdue = due_date < today UTC and status != DONE. */
    private long overdueTasks;
    private Map<String, Long> tasksByStatus;
    private Map<String, Long> tasksByPriority;
    private List<TrendPoint> createdTrend;
    private List<TrendPoint> completedTrend;
}
