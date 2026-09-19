package com.tenant.management.dto;

import com.tenant.management.entity.MemberRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddMemberRequest {
    @NotBlank
    @Email
    private String email;

    @NotNull
    private MemberRole role;
}
