package com.tenant.management.dto;

import com.tenant.management.entity.MemberRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateMemberRoleRequest {
    @NotNull
    private MemberRole role;
}
