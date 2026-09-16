package com.tenant.management.dto;

import com.tenant.management.entity.MemberRole;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMembershipRequest {

    @NotNull(message = "Role is required")
    private MemberRole role;
}
