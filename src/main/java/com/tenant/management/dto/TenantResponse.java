package com.tenant.management.dto;

import com.tenant.management.entity.Tenant;
import com.tenant.management.entity.TenantStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantResponse {

    private Long id;
    private String name;
    private String slug;
    private String description;
    private TenantStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static TenantResponse fromEntity(Tenant tenant) {
        return TenantResponse.builder()
                .id(tenant.getId())
                .name(tenant.getName())
                .slug(tenant.getSlug())
                .description(tenant.getDescription())
                .status(tenant.getStatus())
                .createdAt(tenant.getCreatedAt())
                .updatedAt(tenant.getUpdatedAt())
                .build();
    }
}
