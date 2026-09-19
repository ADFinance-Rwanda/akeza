package com.tenant.management.repository;

import com.tenant.management.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AuditLogRepository extends Repository<AuditLog, Long> {

    AuditLog save(AuditLog log);

    Page<AuditLog> findAllByOrganizationIdOrderByCreatedAtDesc(Long organizationId, Pageable pageable);

    @Query("""
            select a from AuditLog a
            where a.organizationId = :orgId
              and (:action = '' or a.action = :action)
              and (:userId is null or a.userId = :userId)
              and (:entityType = '' or a.entityType = :entityType)
              and (cast(:fromTime as timestamp) is null or a.createdAt >= :fromTime)
              and (cast(:toTime as timestamp) is null or a.createdAt < :toTime)
              and (
                    :q = ''
                    or lower(a.action) like lower(concat('%', :q, '%'))
                    or lower(a.entityType) like lower(concat('%', :q, '%'))
                    or lower(coalesce(a.metadata, '')) like lower(concat('%', :q, '%'))
              )
            order by a.createdAt desc
            """)
    Page<AuditLog> search(
            @Param("orgId") Long orgId,
            @Param("action") String action,
            @Param("userId") Long userId,
            @Param("entityType") String entityType,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime,
            @Param("q") String q,
            Pageable pageable
    );
}
