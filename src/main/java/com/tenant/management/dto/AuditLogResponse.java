package com.tenant.management.dto;

import com.tenant.management.entity.AuditLog;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AuditLogResponse {
    private Long id;
    private Long organizationId;
    private Long userId;
    private String action;
    private String entityType;
    private Long entityId;
    private String metadata;
    private String correlationId;
    private LocalDateTime createdAt;

    public static AuditLogResponse from(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .organizationId(log.getOrganizationId())
                .userId(log.getUserId())
                .action(log.getAction())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .metadata(log.getMetadata())
                .correlationId(log.getCorrelationId())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
