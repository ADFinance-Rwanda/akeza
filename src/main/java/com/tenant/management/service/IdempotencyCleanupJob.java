package com.tenant.management.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class IdempotencyCleanupJob {

    private final IdempotencyReservationService reservationService;

    @Value("${app.idempotency.retention-hours:24}")
    private long retentionHours;

    @Scheduled(initialDelayString = "${app.idempotency.cleanup-interval-ms:3600000}",
            fixedDelayString = "${app.idempotency.cleanup-interval-ms:3600000}")
    public void purgeExpired() {
        reservationService.purgeCompletedBefore(LocalDateTime.now().minusHours(retentionHours));
    }
}
