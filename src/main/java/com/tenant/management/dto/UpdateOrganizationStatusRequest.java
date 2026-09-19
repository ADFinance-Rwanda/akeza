package com.tenant.management.dto;

import com.tenant.management.entity.OrganizationStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateOrganizationStatusRequest {
    @NotNull
    private OrganizationStatus status;
}
