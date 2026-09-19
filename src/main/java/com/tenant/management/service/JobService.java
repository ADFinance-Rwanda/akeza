package com.tenant.management.service;

import com.tenant.management.entity.Job;
import com.tenant.management.entity.JobStatus;
import com.tenant.management.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class JobService {

    public static final String INVALIDATE_ANALYTICS = "INVALIDATE_ANALYTICS";
    public static final String SCAN_OVERDUE = "SCAN_OVERDUE";

    private final JobRepository jobRepository;

    @Transactional
    public void enqueueAnalyticsInvalidation(Long organizationId) {
        jobRepository.save(Job.builder()
                .organizationId(organizationId)
                .type(INVALIDATE_ANALYTICS)
                .payload("{\"organizationId\":" + organizationId + "}")
                .status(JobStatus.PENDING)
                .attempts(0)
                .maxAttempts(5)
                .availableAt(LocalDateTime.now())
                .build());
    }

    @Transactional
    public Job enqueueOverdueScan() {
        if (jobRepository.existsByTypeAndStatus(SCAN_OVERDUE, JobStatus.PENDING)
                || jobRepository.existsByTypeAndStatus(SCAN_OVERDUE, JobStatus.PROCESSING)) {
            return null;
        }
        return jobRepository.save(Job.builder()
                .type(SCAN_OVERDUE)
                .payload("{}")
                .status(JobStatus.PENDING)
                .attempts(0)
                .maxAttempts(5)
                .availableAt(LocalDateTime.now())
                .build());
    }
}
