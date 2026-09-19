package com.tenant.management.dto;

import com.tenant.management.entity.Job;
import com.tenant.management.entity.JobStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class JobResponse {
    private Long id;
    private Long organizationId;
    private String type;
    private String payload;
    private JobStatus status;
    private int attempts;
    private int maxAttempts;
    private String lastError;
    private LocalDateTime availableAt;
    private LocalDateTime deadLetteredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static JobResponse from(Job job) {
        return JobResponse.builder()
                .id(job.getId())
                .organizationId(job.getOrganizationId())
                .type(job.getType())
                .payload(job.getPayload())
                .status(job.getStatus())
                .attempts(job.getAttempts())
                .maxAttempts(job.getMaxAttempts())
                .lastError(job.getLastError())
                .availableAt(job.getAvailableAt())
                .deadLetteredAt(job.getDeadLetteredAt())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }
}
