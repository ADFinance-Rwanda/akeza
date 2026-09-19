package com.tenant.management.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenant.management.entity.IdempotencyKey;
import com.tenant.management.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyReservationService reservationService;
    private final ObjectMapper objectMapper;

    @Value("${app.idempotency.wait-ms:50}")
    private long waitMs;

    @Value("${app.idempotency.wait-attempts:40}")
    private int waitAttempts;

    @Value("${app.idempotency.stale-seconds:30}")
    private long staleSeconds;

    public <T> ResponseEntity<T> execute(
            Long organizationId,
            Long userId,
            String key,
            Object requestBody,
            Class<T> responseType,
            Supplier<T> action
    ) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }
        String normalized = key.trim();
        if (normalized.length() > 100) {
            throw new IllegalArgumentException("Idempotency-Key must be at most 100 characters");
        }
        String hash = hash(requestBody);

        for (int attempt = 0; attempt < waitAttempts; attempt++) {
            Optional<IdempotencyKey> existing = reservationService.find(organizationId, userId, normalized);
            if (existing.isPresent()) {
                IdempotencyKey stored = existing.get();
                if (stored.getResponseStatus() != 0) {
                    return replay(stored, hash, responseType);
                }
                if (isStale(stored) && reservationService.reclaimStale(stored.getId(), staleCutoff()) == 1) {
                    continue;
                }
                sleep();
                continue;
            }

            IdempotencyKey reserved;
            try {
                reserved = reservationService.reserve(organizationId, userId, normalized, hash);
            } catch (RuntimeException ex) {
                if (!isUniqueConflict(ex)) {
                    throw ex;
                }
                sleep();
                continue;
            }

            try {
                T body = action.get();
                reservationService.complete(reserved.getId(), 201, objectMapper.writeValueAsString(body));
                return ResponseEntity.status(201).body(body);
            } catch (JsonProcessingException ex) {
                reservationService.abandon(reserved.getId());
                throw new IllegalStateException("Idempotent response could not be stored");
            } catch (RuntimeException ex) {
                reservationService.abandon(reserved.getId());
                throw ex;
            }
        }
        throw new ConflictException("A request with this Idempotency-Key is already in progress");
    }

    private boolean isStale(IdempotencyKey stored) {
        return stored.getCreatedAt() != null && stored.getCreatedAt().isBefore(staleCutoff());
    }

    private LocalDateTime staleCutoff() {
        return LocalDateTime.now().minusSeconds(staleSeconds);
    }

    private void sleep() {
        try {
            Thread.sleep(waitMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ConflictException("A request with this Idempotency-Key is already in progress");
        }
    }

    private <T> ResponseEntity<T> replay(IdempotencyKey stored, String hash, Class<T> responseType) {
        if (!stored.getRequestHash().equals(hash)) {
            throw new ConflictException("Idempotency-Key was reused with a different request body");
        }
        try {
            T body = objectMapper.readValue(stored.getResponseBody(), responseType);
            return ResponseEntity.status(stored.getResponseStatus()).body(body);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored idempotent response could not be read");
        }
    }

    private boolean isUniqueConflict(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof DataIntegrityViolationException) {
                return true;
            }
            String message = current.getMessage() == null ? "" : current.getMessage();
            if (message.contains("uk_idempotency") || message.contains("Unique index") || message.contains("23505")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String hash(Object body) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(body == null ? "" : body);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(json);
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not hash idempotent request");
        }
    }
}
