package com.tenant.management.service;

import com.tenant.management.entity.AuditLog;
import com.tenant.management.repository.AuditLogRepository;
import com.tenant.management.security.CorrelationIdFilter;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long organizationId, Long userId, String action, String entityType, Long entityId, String metadata) {
        auditLogRepository.save(AuditLog.builder()
                .organizationId(organizationId)
                .userId(userId)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .metadata(metadata)
                .correlationId(MDC.get(CorrelationIdFilter.MDC_KEY))
                .build());
    }
}
