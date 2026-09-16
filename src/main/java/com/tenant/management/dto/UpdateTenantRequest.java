package com.tenant.management.dto;

import com.tenant.management.entity.TenantStatus;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTenantRequest {

    @Size(min = 1, max = 150, message = "Tenant name must be between 1 and 150 characters")
    private String name;

    @Size(min = 1, max = 80, message = "Tenant slug must be between 1 and 80 characters")
    @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "Slug must be lowercase letters, numbers, and hyphens")
    private String slug;

    @Size(max = 500, message = "Description must be at most 500 characters")
    private String description;

    private TenantStatus status;
}
