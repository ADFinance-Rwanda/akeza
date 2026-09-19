package com.tenant.management.service;

import com.tenant.management.entity.Job;
import com.tenant.management.entity.JobStatus;
import com.tenant.management.entity.Task;
import com.tenant.management.entity.TaskStatus;
import com.tenant.management.repository.JobRepository;
import com.tenant.management.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobProcessor {

    static final Clock CLOCK = Clock.systemUTC();

    private final JobRepository jobRepository;
    private final AnalyticsService analyticsService;
    private final TaskRepository taskRepository;
    private final AuditService auditService;

    @Transactional
    public int processDue() {
        jobRepository.reclaimStale(JobStatus.PENDING, JobStatus.PROCESSING, LocalDateTime.now().minusMinutes(2));
        List<Job> due = jobRepository.findDue(JobStatus.PENDING, LocalDateTime.now(), PageRequest.of(0, 25));
        int processed = 0;
        for (Job job : due) {
            if (jobRepository.claim(job.getId(), JobStatus.PROCESSING, JobStatus.PENDING) != 1) {
                continue;
            }
            Job current = jobRepository.findById(job.getId()).orElseThrow();
            try {
                handle(current);
                current.setStatus(JobStatus.DONE);
                current.setLastError(null);
            } catch (IllegalArgumentException ex) {
                log.warn("Job {} rejected: {}", current.getId(), ex.getMessage());
                current.setLastError(truncate(ex.getMessage()));
                current.setStatus(JobStatus.FAILED);
                current.setDeadLetteredAt(LocalDateTime.now());
            } catch (Exception ex) {
                log.warn("Job {} failed attempt {}: {}", current.getId(), current.getAttempts(), ex.getMessage());
                current.setLastError(truncate(ex.getMessage()));
                if (current.getAttempts() >= current.getMaxAttempts()) {
                    current.setStatus(JobStatus.FAILED);
                    current.setDeadLetteredAt(LocalDateTime.now());
                } else {
                    current.setStatus(JobStatus.PENDING);
                    current.setAvailableAt(LocalDateTime.now().plusSeconds(5L * current.getAttempts()));
                }
            }
            jobRepository.save(current);
            processed++;
        }
        return processed;
    }

    private void handle(Job job) {
        if (JobService.INVALIDATE_ANALYTICS.equals(job.getType()) && job.getOrganizationId() != null) {
            analyticsService.evict(job.getOrganizationId());
            return;
        }
        if (JobService.SCAN_OVERDUE.equals(job.getType())) {
            scanOverdue();
            return;
        }
        throw new IllegalArgumentException("Unsupported job type: " + job.getType());
    }

    private void scanOverdue() {
        LocalDate today = LocalDate.now(CLOCK);
        LocalDateTime now = LocalDateTime.now(CLOCK);
        List<Task> overdue = taskRepository.findUnnotifiedOverdue(today, TaskStatus.DONE);
        for (Task task : overdue) {
            task.setOverdueNotifiedAt(now);
            taskRepository.save(task);
            Long orgId = task.getOrganization() == null ? null : task.getOrganization().getId();
            auditService.record(orgId, null, "TASK_OVERDUE", "Task", task.getId(), task.getTitle());
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return "unknown error";
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
