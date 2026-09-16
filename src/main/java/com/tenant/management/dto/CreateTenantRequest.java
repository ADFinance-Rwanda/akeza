package com.tenant.management.dto;

import jakarta.validation.constraints.NotBlank;
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
public class CreateTenantRequest {

    @NotBlank(message = "Tenant name is required")
    @Size(max = 150, message = "Tenant name must be at most 150 characters")
    private String name;

    @NotBlank(message = "Tenant slug is required")
    @Size(max = 80, message = "Tenant slug must be at most 80 characters")
    @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "Slug must be lowercase letters, numbers, and hyphens")
    private String slug;

    @Size(max = 500, message = "Description must be at most 500 characters")
    private String description;
}
