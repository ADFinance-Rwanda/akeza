package com.tenant.management.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "jobs", indexes = @Index(name = "idx_jobs_status_available", columnList = "status, available_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id")
    private Long organizationId;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    @Column(nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(name = "max_attempts", nullable = false)
    @Builder.Default
    private int maxAttempts = 5;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "dead_lettered_at")
    private LocalDateTime deadLetteredAt;

    @Column(name = "available_at", nullable = false)
    private LocalDateTime availableAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (availableAt == null) {
            availableAt = now;
        }
        if (status == null) {
            status = JobStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
