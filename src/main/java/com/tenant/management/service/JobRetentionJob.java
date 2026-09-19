package com.tenant.management.service;

import com.tenant.management.entity.JobStatus;
import com.tenant.management.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobRetentionJob {

    private final JobRepository jobRepository;

    @Value("${app.jobs.done-retention-hours:24}")
    private long doneRetentionHours;

    @Value("${app.jobs.failed-retention-hours:168}")
    private long failedRetentionHours;

    @Scheduled(initialDelayString = "${app.jobs.cleanup-interval-ms:3600000}",
            fixedDelayString = "${app.jobs.cleanup-interval-ms:3600000}")
    @Transactional
    public int purgeExpired() {
        return purge(LocalDateTime.now());
    }

    @Transactional
    public int purge(LocalDateTime now) {
        int done = jobRepository.deleteByStatusAndUpdatedAtBefore(
                JobStatus.DONE, now.minusHours(doneRetentionHours));
        int failed = jobRepository.deleteByStatusAndUpdatedAtBefore(
                JobStatus.FAILED, now.minusHours(failedRetentionHours));
        if (done + failed > 0) {
            log.info("Purged {} DONE and {} FAILED jobs older than retention", done, failed);
        }
        return done + failed;
    }
}
