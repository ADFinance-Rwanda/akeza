package com.tenant.management.repository;

import com.tenant.management.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

public interface AuditLogRepository extends Repository<AuditLog, Long> {

    AuditLog save(AuditLog log);

    Page<AuditLog> findAllByOrganizationIdOrderByCreatedAtDesc(Long organizationId, Pageable pageable);
}
