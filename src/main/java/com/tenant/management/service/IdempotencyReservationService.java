package com.tenant.management.service;

import com.tenant.management.entity.IdempotencyKey;
import com.tenant.management.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IdempotencyReservationService {

    private final IdempotencyKeyRepository idempotencyKeyRepository;

    @Transactional(readOnly = true)
    public Optional<IdempotencyKey> find(Long organizationId, Long userId, String key) {
        return idempotencyKeyRepository.findByOrganizationIdAndUserIdAndKeyValue(organizationId, userId, key);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdempotencyKey reserve(Long organizationId, Long userId, String key, String requestHash) {
        return idempotencyKeyRepository.saveAndFlush(IdempotencyKey.builder()
                .organizationId(organizationId)
                .userId(userId)
                .keyValue(key)
                .requestHash(requestHash)
                .responseStatus(0)
                .responseBody("{}")
                .build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long id, int status, String body) {
        IdempotencyKey stored = idempotencyKeyRepository.findById(id).orElseThrow();
        stored.setResponseStatus(status);
        stored.setResponseBody(body);
        stored.setCompletedAt(LocalDateTime.now());
        idempotencyKeyRepository.saveAndFlush(stored);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void abandon(Long id) {
        idempotencyKeyRepository.findById(id).ifPresent(idempotencyKeyRepository::delete);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int reclaimStale(Long id, LocalDateTime cutoff) {
        return idempotencyKeyRepository.deleteStaleInProgress(id, cutoff);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int purgeCompletedBefore(LocalDateTime cutoff) {
        return idempotencyKeyRepository.deleteCompletedBefore(cutoff);
    }
}
